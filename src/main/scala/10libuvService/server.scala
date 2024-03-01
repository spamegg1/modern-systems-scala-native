package `10libUvService`

import scalanative.unsigned.UnsignedRichLong
import scalanative.unsafe.*
import scalanative.libc.*
import scalanative.unsigned.*
import stdlib.*, stdio.*, string.*
import collection.mutable
import concurrent.{Future, ExecutionContext}

case class Request[T](
    method: String,
    url: String,
    headers: Map[String, String],
    body: T
)
case class Response[T](
    code: Int,
    description: String,
    headers: Map[String, String],
    body: T
)

sealed trait Route:
  val method: String
  val path: String

case class SyncRoute(
    method: String,
    path: String,
    handler: Request[String] => Response[String]
) extends Route

case class AsyncRoute(
    method: String,
    path: String,
    handler: Request[String] => Future[Response[String]]
) extends Route

object Server extends Parsing:
  import LibUVConstants.*, LibUV.*, HttpParser.*

  implicit val ec: ExecutionContext = EventLoop
  val loop = EventLoop.loop
  var serial = 1L
  override val requests = mutable.Map[Long, RequestState]()
  var activeRequests = 0

  // val urlCB: HttpDataCB = new HttpDataCB:
  val urlCB: HttpDataCB =
    CFuncPtr3.fromScalaFunction[Ptr[Parser], CString, Long, Int](
      (p: Ptr[Parser], data: CString, len: Long) => onURL(p, data, len)
    )

  // val onKeyCB: HttpDataCB = new HttpDataCB:
  val onKeyCB: HttpDataCB =
    CFuncPtr3.fromScalaFunction[Ptr[Parser], CString, Long, Int](
      (p: Ptr[Parser], data: CString, len: Long) => onHeaderKey(p, data, len)
    )

  // val onValueCB: HttpDataCB = new HttpDataCB:
  val onValueCB: HttpDataCB =
    CFuncPtr3.fromScalaFunction[Ptr[Parser], CString, Long, Int](
      (p: Ptr[Parser], data: CString, len: Long) => onHeaderValue(p, data, len)
    )

  // val onBodyCB: HttpDataCB = new HttpDataCB:
  val onBodyCB: HttpDataCB =
    CFuncPtr3.fromScalaFunction[Ptr[Parser], CString, Long, Int](
      (p: Ptr[Parser], data: CString, len: Long) => onBody(p, data, len)
    )

  // val completeCB: HttpCB = new HttpCB:
  val completeCB: HttpCB =
    CFuncPtr1.fromScalaFunction[Ptr[Parser], Int]((p: Ptr[Parser]) =>
      onMessageComplete(p)
    )

  val parserSettings = malloc(sizeof[ParserSettings])
    .asInstanceOf[Ptr[ParserSettings]]
  http_parser_settings_init(parserSettings)
  parserSettings._2 = urlCB
  parserSettings._4 = onKeyCB
  parserSettings._5 = onValueCB
  parserSettings._7 = onBodyCB
  parserSettings._8 = completeCB

  var router: Function1[Request[String], Route] = null

  def init(port: Int, f: Request[String] => Route): Unit =
    router = f
    val addr = malloc(64.toUSize) // 0.5
    check(uv_ip4_addr(c"0.0.0.0", 9999, addr), "uv_ip4_addr")
    val server = malloc(uv_handle_size(UV_TCP_T)).asInstanceOf[TCPHandle]
    check(uv_tcp_init(loop, server), "uv_tcp_init")
    check(uv_tcp_bind(server, addr, 0), "uv_tcp_bind")
    check(uv_listen(server, 4096, connectCB), "uv_listen")
    this.activeRequests = 1
    println("running")

  // val connectCB = new ConnectionCB:
  val connectCB =
    CFuncPtr2.fromScalaFunction[TCPHandle, Int, Unit]((server: TCPHandle, status: Int) =>
      println(s"connection incoming with status $status")
      val client = malloc(uv_handle_size(UV_TCP_T)).asInstanceOf[TCPHandle]
      val id = serial
      serial += 1

      val state = malloc(sizeof[ConnectionState])
        .asInstanceOf[Ptr[ConnectionState]]
      state._1 = serial
      state._2 = client
      http_parser_init(state.at3, HTTP_REQUEST)
      (state.at3)._8 = state.asInstanceOf[Ptr[Byte]]
      !(client.asInstanceOf[Ptr[Ptr[Byte]]]) = state.asInstanceOf[Ptr[Byte]]

      stdio.printf(c"initialized handle at %x, parser at %x\n", client, state)

      check(uv_tcp_init(loop, client), "uv_tcp_init (client)")
      check(uv_accept(server, client), "uv_accept")
      check(uv_read_start(client, allocCB, readCB), "uv_read_start")
    )

  // val allocCB = new AllocCB:
  val allocCB =
    CFuncPtr3.fromScalaFunction[TCPHandle, CSize, Ptr[Buffer], Unit](
      (handle: TCPHandle, size: CSize, buffer: Ptr[Buffer]) =>
        val buf = stdlib.malloc(4096.toUSize) // 0.5
        // buf(4095) = 0 // null termination?
        buffer._1 = buf
        buffer._2 = 4095.toUSize // 0.5
    )

  // val readCB = new ReadCB:
  val readCB =
    CFuncPtr3.fromScalaFunction[TCPHandle, CSSize, Ptr[Buffer], Unit](
      (handle: TCPHandle, size: CSSize, buffer: Ptr[Buffer]) =>
        val statePtr = handle.asInstanceOf[Ptr[Ptr[ConnectionState]]]
        val parser = (!statePtr).at3
        val messageId = (!statePtr)._1
        println(s"conn $messageId: read message of size $size")

        if size < 0 then
          uv_close(handle, null)
          stdlib.free(buffer._1)
        else
          http_parser_execute(parser, parserSettings, buffer._1, size.toLong) // 0.5
          stdlib.free(buffer._1)
    )

  override def handleRequest(
      id: Long,
      client: TCPHandle,
      r: RequestState
  ): Unit =
    println(s"got complete request $id: $r\n")
    val request = Request(r.method, r.url, r.headerMap.toMap, r.body)
    val route = router(request)
    route match
      case SyncRoute(_, _, handler) =>
        val resp = handler(request)
        println("sending sync response")
        sendResponse(id, client, resp)
      case AsyncRoute(_, _, handler) =>
        val resp = handler(request)
        resp.map(r =>
          println("about to send async response")
          sendResponse(id, client, r)
        )
        println("returning immediately, async handler invoked")

  def sendResponseAsync(
      id: Long,
      client: TCPHandle,
      resp: Future[Response[String]]
  ): Unit =
    resp.map(r =>
      println("async?")
      sendResponse(id, client, r)
    )

  def sendResponse(
      id: Long,
      client: TCPHandle,
      resp: Response[String]
  ): Unit =
    var respString = s"HTTP/1.1 ${resp.code} ${resp.description}\r\n"
    val headers =
      if !resp.headers.contains("Content-Length") then
        resp.headers + ("Content-Length" -> resp.body.size)
      else resp.headers

    for (k, v) <- headers do respString += s"${k}: $v\r\n"

    respString += s"\r\n${resp.body}"

    val buffer = malloc(sizeof[Buffer]).asInstanceOf[Ptr[Buffer]]
    Zone { // implicit z => // 0.5
      val tempResponse = toCString(respString)
      val responseLen = strlen(tempResponse) // removed +1
      buffer._1 = malloc(responseLen)
      buffer._2 = responseLen
      strncpy(buffer._1, tempResponse, responseLen)
    }
    stdio.printf(c"response buffer:\n%s\n", buffer._1)

    val writeReq = malloc(uv_req_size(UV_WRITE_REQ_T)).asInstanceOf[WriteReq]
    !writeReq = buffer.asInstanceOf[Ptr[Byte]]
    check(uv_write(writeReq, client, buffer, 1, writeCB), "uv_write")

  // val writeCB = new WriteCB:
  val writeCB =
    CFuncPtr2.fromScalaFunction[WriteReq, Int, Unit]((writeReq: WriteReq, status: Int) =>
      println("write completed")
      val responseBuffer = (!writeReq).asInstanceOf[Ptr[Buffer]]
      stdlib.free(responseBuffer._1)
      stdlib.free(responseBuffer.asInstanceOf[Ptr[Byte]])
      stdlib.free(writeReq.asInstanceOf[Ptr[Byte]])
    )
