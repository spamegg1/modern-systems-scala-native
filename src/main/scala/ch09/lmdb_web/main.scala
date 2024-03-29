package `09lmdbWeb`

import scalanative.unsigned.{UnsignedRichLong, UnsignedRichInt}
import scalanative.unsafe.*
import scalanative.libc.*
import stdlib.*
import string.*
import argonaut.*
import Argonaut.*

val addPatn = raw"/add/([^/]+)/([^/]+)".r
val fetchPatn = raw"/fetch/([^/]+)".r
val listPatn = raw"/list/([^/]+)".r

// @main
def lmbdWebMain1(args: String*): Unit =
  val env = LMDB.open(c"./db")
  Server.serve_http(
    8080,
    request =>
      request.uri match
        case addPatn(setKey, key) =>
          val data = parseBody[Map[String, String]](request)
          val set = LMDB.getJson[List[String]](env, setKey)
          val newSet = key :: set
          LMDB.putJson(env, setKey, newSet)
          LMDB.putJson(env, key, data)
          makeResponse("OK")
        case fetchPatn(key) =>
          val item = LMDB.getJson[Map[String, String]](env, key)
          makeResponse(item)
        case listPatn(setKey) =>
          val set = LMDB.getJson[List[String]](env, setKey)
          makeResponse(set)
        case _ => makeResponse("no route match\n")
  )

def parseBody[T](request: HttpRequest)(implicit dec: DecodeJson[T]): T =
  request.body.decodeOption[T].get

def makeResponse[T](resp: T)(implicit enc: EncodeJson[T]): HttpResponse =
  val respString = resp.asJson.spaces2
  val size = respString.size.toString
  HttpResponse(200, Map("Content-Length" -> size), respString)

// val listOffsetPatn = raw"/list/([^/]+)?offset=([^/]+)".r

def lmbdWebMain2(args: String*): Unit =
  val env = LMDB.open(c"./db")
  Server.serve_http(
    8080,
    request =>
      request.uri match
        case addPatn(setKey, key) =>
          val data = parseBody[Map[String, String]](request)
          val set = LMDB.getJson[List[String]](env, setKey)
          val newSet = key :: set
          LMDB.putJson(env, setKey, newSet)
          LMDB.putJson(env, key, data)
          makeResponse("OK")
        case fetchPatn(key) =>
          val item = LMDB.getJson[Map[String, String]](env, key)
          makeResponse(item)
        case listPatn(setKey) =>
          val set = LMDB.getJson[List[String]](env, setKey)
          makeResponse(set)
  )

object Server:
  import LibUV.*
  import LibUVConstants.*
  import HTTP.RequestHandler

  type ClientState = CStruct3[Ptr[Byte], CSize, CSize]

  val loop = uv_default_loop()

  // val connectionCB = new ConnectionCB:
  val connectionCB =
    CFuncPtr2.fromScalaFunction[TCPHandle, Int, Unit]((handle: TCPHandle, status: Int) =>
      // println("received connection")
      val client = malloc(uv_handle_size(UV_TCP_T)).asInstanceOf[TCPHandle]
      check_error(uv_tcp_init(loop, client), "uv_tcp_init(client)")
      check_error(uv_accept(handle, client), "uv_accept")
      check_error(uv_read_start(client, allocCB, readCB), "uv_read_start")
    )

  var router: RequestHandler = (_ => ???)

  def serve_http(port: Int, handler: RequestHandler): Unit =
    println(s"about to serve on port ${port}")
    this.router = handler
    serve_tcp(c"0.0.0.0", port, 0, 4096, connectionCB)

  def serve_tcp(
      address: CString,
      port: Int,
      flags: Int,
      backlog: Int,
      callback: ConnectionCB
  ): Unit =
    val addr = stackalloc[Byte](sizeof[Byte])
    val addrConvert = uv_ip4_addr(address, port, addr)
    val handle = malloc(uv_handle_size(UV_TCP_T)).asInstanceOf[TCPHandle]
    check_error(uv_tcp_init(loop, handle), "uv_tcp_init(server)")
    check_error(uv_tcp_bind(handle, addr, flags), "uv_tcp_bind")
    check_error(uv_listen(handle, backlog, callback), "uv_tcp_listen")
    uv_run(loop, UV_RUN_DEFAULT)
    ()

  // val allocCB = new AllocCB:
  val allocCB =
    CFuncPtr3.fromScalaFunction[TCPHandle, CSize, Ptr[Buffer], Unit](
      (client: TCPHandle, size: CSize, buffer: Ptr[Buffer]) =>
        val buf = stdlib.malloc(4096.toUSize) // 0.5
        buffer._1 = buf
        buffer._2 = 4096.toUSize // 0.5
    )

  def append_data(
      state: Ptr[ClientState],
      size: CSSize,
      buffer: Ptr[Buffer]
  ): Unit =
    val copyPosition = state._1 + state._3
    string.strncpy(copyPosition, buffer._1, size.toUSize) // 0.5
    state._3 = state._3 + size.toUSize // 0.5

  def send_response(client: TCPHandle, response: HttpResponse): Unit =
    val req = malloc(uv_req_size(UV_WRITE_REQ_T)).asInstanceOf[WriteReq]
    val responseBuffer = malloc(sizeof[Buffer]).asInstanceOf[Ptr[Buffer]]
    responseBuffer._1 = malloc(4096.toUSize) // 0.5
    responseBuffer._2 = 4096.toUSize // 0.5
    HTTP.make_response(response, responseBuffer)
    responseBuffer._2 = string.strlen(responseBuffer._1)
    !req = responseBuffer.asInstanceOf[Ptr[Byte]]
    check_error(uv_write(req, client, responseBuffer, 1, writeCB), "uv_write")

  def shutdown(client: TCPHandle): Unit =
    val shutdown_req =
      malloc(uv_req_size(UV_SHUTDOWN_REQ_T)).asInstanceOf[ShutdownReq]
    !shutdown_req = client.asInstanceOf[Ptr[Byte]]
    check_error(uv_shutdown(shutdown_req, client, shutdownCB), "uv_shutdown")

  // val readCB = new ReadCB:
  val readCB =
    CFuncPtr3.fromScalaFunction[TCPHandle, CSSize, Ptr[Buffer], Unit](
      (client: TCPHandle, size: CSSize, buffer: Ptr[Buffer]) =>
        if size < 0 then shutdown(client)
        else
          try
            val parsed_request = HTTP.parseRequest(buffer._1, size.toLong) // 0.5
            val response = router(parsed_request)
            send_response(client, response)
            shutdown(client)
          catch
            case e: Throwable =>
              println(s"error during parsing: ${e}")
              shutdown(client)
    )

  // val writeCB = new WriteCB:
  val writeCB =
    CFuncPtr2.fromScalaFunction[WriteReq, Int, Unit]((writeReq: WriteReq, status: Int) =>
      // println("write completed")
      val responseBuffer = (!writeReq).asInstanceOf[Ptr[Buffer]]
      stdlib.free(responseBuffer._1)
      stdlib.free(responseBuffer.asInstanceOf[Ptr[Byte]])
      stdlib.free(writeReq.asInstanceOf[Ptr[Byte]])
    )

  // val shutdownCB = new ShutdownCB:
  val shutdownCB = CFuncPtr2.fromScalaFunction[ShutdownReq, Int, Unit](
    (shutdownReq: ShutdownReq, status: Int) =>
      // println("all pending writes complete, closing TCP connection")
      val client = (!shutdownReq).asInstanceOf[TCPHandle]
      // check_error(uv_close(client,closeCB),"uv_close")
      uv_close(client, closeCB)
      stdlib.free(shutdownReq.asInstanceOf[Ptr[Byte]])
  )

  // val closeCB = new CloseCB:
  val closeCB =
    CFuncPtr1.fromScalaFunction[TCPHandle, Unit]((client: TCPHandle) =>
      stdlib.free(client.asInstanceOf[Ptr[Byte]])
      // println("closed client connection")
    )

@link("uv")
@extern
object LibUV:
  type TimerHandle = Ptr[Byte]

  type Loop = Ptr[Byte]
  type TimerCB = CFuncPtr1[TimerHandle, Unit]

  def uv_default_loop(): Loop = extern
  def uv_loop_size(): CSize = extern
  def uv_is_active(handle: Ptr[Byte]): Int = extern
  def uv_handle_size(h_type: Int): CSize = extern
  def uv_req_size(r_type: Int): CSize = extern

  def uv_timer_init(loop: Loop, handle: TimerHandle): Int = extern
  def uv_timer_start(
      handle: TimerHandle,
      cb: TimerCB,
      timeout: Long,
      repeat: Long
  ): Int = extern
  def uv_timer_stop(handle: TimerHandle): Int = extern

  def uv_run(loop: Loop, runMode: Int): Int = extern

  def uv_strerror(err: Int): CString = extern
  def uv_err_name(err: Int): CString = extern

  type Buffer = CStruct2[Ptr[Byte], CSize]
  type TCPHandle = Ptr[Ptr[Byte]]
  type ConnectionCB = CFuncPtr2[TCPHandle, Int, Unit]
  type WriteReq = Ptr[Ptr[Byte]]
  type ShutdownReq = Ptr[Ptr[Byte]]

  def uv_tcp_init(loop: Loop, tcp_handle: TCPHandle): Int = extern
  def uv_tcp_bind(tcp_handle: TCPHandle, address: Ptr[Byte], flags: Int): Int =
    extern
  def uv_listen(
      stream_handle: TCPHandle,
      backlog: Int,
      uv_connection_cb: ConnectionCB
  ): Int = extern
  def uv_accept(server: TCPHandle, client: TCPHandle): Int = extern
  def uv_read_start(client: TCPHandle, allocCB: AllocCB, readCB: ReadCB): Int =
    extern
  def uv_write(
      writeReq: WriteReq,
      client: TCPHandle,
      bufs: Ptr[Buffer],
      numBufs: Int,
      writeCB: WriteCB
  ): Int = extern
  def uv_shutdown(
      shutdownReq: ShutdownReq,
      client: TCPHandle,
      shutdownCB: ShutdownCB
  ): Int = extern
  def uv_close(handle: TCPHandle, closeCB: CloseCB): Int = extern

  def uv_ip4_addr(address: CString, port: Int, out_addr: Ptr[Byte]): Int =
    extern
  def uv_ip4_name(address: Ptr[Byte], s: CString, size: Int): Int = extern

  type AllocCB = CFuncPtr3[TCPHandle, CSize, Ptr[Buffer], Unit]
  type ReadCB = CFuncPtr3[TCPHandle, CSSize, Ptr[Buffer], Unit]
  type WriteCB = CFuncPtr2[WriteReq, Int, Unit]
  type ShutdownCB = CFuncPtr2[ShutdownReq, Int, Unit]
  type CloseCB = CFuncPtr1[TCPHandle, Unit]

object LibUVConstants:
  import LibUV.*

  // uv_run_mode
  val UV_RUN_DEFAULT = 0
  val UV_RUN_ONCE = 1
  val UV_RUN_NOWAIT = 2

  // UV_HANDLE_T
  val UV_PIPE_T = 7
  val UV_POLL_T = 8
  val UV_PREPARE_T = 9
  val UV_PROCESS_T = 10
  val UV_TCP_T = 12
  val UV_TIMER_T = 13
  val UV_TTY_T = 14
  val UV_UDP_T = 15

  // UV_REQ_T
  val UV_WRITE_REQ_T = 3
  val UV_SHUTDOWN_REQ_T = 4

  val UV_READABLE = 1
  val UV_WRITABLE = 2
  val UV_DISCONNECT = 4
  val UV_PRIORITIZED = 8

  def check_error(v: Int, label: String): Int =
    if v == 0 then
      // println(s"$label returned $v")
      v
    else
      val error = fromCString(uv_err_name(v))
      val message = fromCString(uv_strerror(v))
      println(s"$label returned $v: $error: $message")
      v
