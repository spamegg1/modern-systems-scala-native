package ch06
package asyncHttp

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.{CQuote, Ptr, CSize, CSSize, CString, sizeof, stackalloc}
import scalanative.unsafe.{CStruct3, CFuncPtr1, CFuncPtr2, CFuncPtr3}
import scalanative.libc.{stdlib, string, stdio}
import stdlib.malloc

import LibUV.*, LibUVConstants.*
import HTTP.RequestHandler
import ch03.http.{HttpRequest, HttpResponse}

type ClientState = CStruct3[Ptr[Byte], CSize, CSize]

@main
def asyncHttp(args: String): Unit =
  serveHttp(
    8080,
    request => HttpResponse(200, Map("Content-Length" -> "12"), "hello world\n")
  )

var router: RequestHandler = _ =>
  HttpResponse(200, Map("Content-Length" -> "12"), "hello world\n")

def serveHttp(port: Int, handler: RequestHandler): Unit =
  println(s"about to serve on port ${port}")
  this.router = handler
  serveTcp(c"0.0.0.0", port, 0, 4096, connectionCB)

val readCB = CFuncPtr3.fromScalaFunction[TCPHandle, CSSize, Ptr[Buffer], Unit]:
  (client: TCPHandle, size: CSSize, buffer: Ptr[Buffer]) =>
    if size < 0 then shutdown(client)
    else
      try
        val parsedRequest = HTTP.parseRequest(buffer._1, size.toLong) // 0.5
        val response = router(parsedRequest)
        sendResponse(client, response)
        shutdown(client)
      catch
        case e: Throwable =>
          println(s"error during parsing: ${e}")
          shutdown(client)

def sendResponse(client: TCPHandle, response: HttpResponse): Unit =
  val req = malloc(uv_req_size(UV_WRITE_REQ_T)).asInstanceOf[WriteReq]
  val responseBuffer = malloc(sizeof[Buffer]).asInstanceOf[Ptr[Buffer]]

  responseBuffer._1 = malloc(4096) // 0.5
  responseBuffer._2 = 4096.toUSize // 0.5

  HTTP.makeResponse(response, responseBuffer)
  responseBuffer._2 = string.strlen(responseBuffer._1)

  !req = responseBuffer.asInstanceOf[Ptr[Byte]]
  checkError(uv_write(req, client, responseBuffer, 1, writeCB), "uv_write")

val loop = uv_default_loop()

def serveTcp(
    address: CString,
    port: Int,
    flags: Int,
    backlog: Int,
    callback: ConnectionCB
): Unit =
  val addr = stackalloc[Byte](1)
  val addrConvert = uv_ip4_addr(address, port, addr)
  println(s"uv_ip4_addr returned $addrConvert")

  val handle = malloc(uv_handle_size(UV_TCP_T)).asInstanceOf[TCPHandle]
  checkError(uv_tcp_init(loop, handle), "uv_tcp_init(server)")
  checkError(uv_tcp_bind(handle, addr, flags), "uv_tcp_bind")
  checkError(uv_listen(handle, backlog, callback), "uv_tcp_listen")

  uv_run(loop, UV_RUN_DEFAULT)

val connectionCB = CFuncPtr2.fromScalaFunction[TCPHandle, Int, Unit]:
  (handle: TCPHandle, status: Int) =>
    println("received connection")

    // initialize the new client tcp handle and its state
    val client = malloc(uv_handle_size(UV_TCP_T)).asInstanceOf[TCPHandle]
    checkError(uv_tcp_init(loop, client), "uv_tcp_init(client)")

    var clientStatePtr = (!client).asInstanceOf[Ptr[ClientState]]
    clientStatePtr = initializeClientState(client)

    // accept the incoming connection into the new handle
    checkError(uv_accept(handle, client), "uv_accept")

    // set up callbacks for incoming data
    checkError(uv_read_start(client, allocCB, readCB), "uv_read_start")

def initializeClientState(client: TCPHandle): Ptr[ClientState] =
  val clientStatePtr = malloc(sizeof[ClientState]).asInstanceOf[Ptr[ClientState]]

  stdio.printf(
    c"allocated data at %x; assigning into handle storage at %x\n",
    clientStatePtr,
    client
  )

  val clientStateData = malloc(4096) // 0.5
  clientStatePtr._1 = clientStateData
  clientStatePtr._2 = 4096.toUSize // total // 0.5
  clientStatePtr._3 = 0.toUSize // used // 0.5

  !client = clientStatePtr.asInstanceOf[Ptr[Byte]]
  clientStatePtr

val allocCB = CFuncPtr3.fromScalaFunction[TCPHandle, CSize, Ptr[Buffer], Unit]:
  (client: TCPHandle, size: CSize, buffer: Ptr[Buffer]) =>
    println("allocating 4096 bytes")
    val buf = malloc(4096) // 0.5
    buffer._1 = buf
    buffer._2 = 4096.toUSize // 0.5

def appendData(state: Ptr[ClientState], size: CSSize, buffer: Ptr[Buffer]): Unit =
  val copyPosition = state._1 + state._3
  string.strncpy(copyPosition, buffer._1, size.toUSize) // 0.5

  // be sure to update the length of the data since we have copied into it
  state._3 = state._3 + size.toUSize // 0.5

  stdio.printf(c"client %x: %d/%d bytes used\n", state, state._3, state._2)

def sendResponse(client: TCPHandle, state: Ptr[ClientState]): Unit =
  val resp = malloc(uv_req_size(UV_WRITE_REQ_T)).asInstanceOf[WriteReq]
  val responseBuffer = malloc(sizeof[Buffer]).asInstanceOf[Ptr[Buffer]]
  responseBuffer._1 = makeResponse(state)
  responseBuffer._2 = string.strlen(responseBuffer._1)
  !resp = responseBuffer.asInstanceOf[Ptr[Byte]]
  checkError(uv_write(resp, client, responseBuffer, 1, writeCB), "uv_write")

def makeResponse(state: Ptr[ClientState]): CString =
  val responseFormat = c"received response:\n%s\n"
  val responseData = malloc(string.strlen(responseFormat) + state._3)
  stdio.sprintf(responseData, responseFormat, state._1)
  responseData

val writeCB = CFuncPtr2.fromScalaFunction[WriteReq, Int, Unit]:
  (writeReq: WriteReq, status: Int) =>
    println("write completed")
    val responseBuffer = (!writeReq).asInstanceOf[Ptr[Buffer]]
    stdlib.free(responseBuffer._1)
    stdlib.free(responseBuffer.asInstanceOf[Ptr[Byte]])
    stdlib.free(writeReq.asInstanceOf[Ptr[Byte]])

def shutdown(client: TCPHandle): Unit =
  val shutdownReq = malloc(uv_req_size(UV_SHUTDOWN_REQ_T)).asInstanceOf[ShutdownReq]
  !shutdownReq = client.asInstanceOf[Ptr[Byte]]
  checkError(uv_shutdown(shutdownReq, client, shutdownCB), "uv_shutdown")

val shutdownCB = CFuncPtr2.fromScalaFunction[ShutdownReq, Int, Unit]:
  (shutdownReq: ShutdownReq, status: Int) =>
    println("all pending writes complete, closing TCP connection")
    val client = (!shutdownReq).asInstanceOf[TCPHandle]
    checkError(uv_close(client, closeCB), "uv_close")
    stdlib.free(shutdownReq.asInstanceOf[Ptr[Byte]])

val closeCB = CFuncPtr1.fromScalaFunction[TCPHandle, Unit]: (client: TCPHandle) =>
  println("closed client connection")
  val clientStatePtr = (!client).asInstanceOf[Ptr[ClientState]]
  stdlib.free(clientStatePtr._1)
  stdlib.free(clientStatePtr.asInstanceOf[Ptr[Byte]])
  stdlib.free(client.asInstanceOf[Ptr[Byte]])
