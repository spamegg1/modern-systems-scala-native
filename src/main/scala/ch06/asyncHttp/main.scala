package ch06
package asyncHttp

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.{CQuote, Ptr, CSize, CSSize, CString, sizeof, stackalloc}
import scalanative.unsafe.{CStruct3, CFuncPtr1, CFuncPtr2, CFuncPtr3}
import scalanative.libc.{stdlib, string, stdio}
import stdlib.malloc

import LibUV.*, LibUVConstants.*
import HTTP.RequestHandler
import ch03.httpClient.{HttpRequest, HttpResponse}

@main
def run(args: String): Unit = serveHttp(8080, router)

val router: RequestHandler = _ => // was var
  HttpResponse(200, Map("Content-Length" -> "12"), "hello world\n")

def serveHttp(port: Int, handler: RequestHandler): Unit =
  println(s"about to serve on port ${port}")
  // this.router = handler // ???
  serveTcp(c"0.0.0.0", port, 0, 4096, connectionCB)

// cannot be factored out due to readCB differences.
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

// cannot be factored out due to differences.
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

// cannot be factored out due to differences.
def sendResponse(client: TCPHandle, response: HttpResponse): Unit =
  val req = malloc(uv_req_size(UV_WRITE_REQ_T)).asInstanceOf[WriteReq]
  val responseBuffer = malloc(sizeof[Buffer]).asInstanceOf[Ptr[Buffer]]
  responseBuffer._1 = malloc(4096) // 0.5
  responseBuffer._2 = 4096.toUSize // 0.5
  HTTP.makeResponse(response, responseBuffer)
  responseBuffer._2 = string.strlen(responseBuffer._1)
  !req = responseBuffer.asInstanceOf[Ptr[Byte]]
  checkError(uv_write(req, client, responseBuffer, 1, writeCB), "uv_write")
