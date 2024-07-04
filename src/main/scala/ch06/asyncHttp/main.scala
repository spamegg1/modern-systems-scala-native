package ch06
package asyncHttp

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.{CQuote, Ptr, CSize, CSSize, CString, sizeof, stackalloc}
import scalanative.unsafe.{CStruct3, CFuncPtr1, CFuncPtr2, CFuncPtr3}
import scalanative.libc.{stdlib, string, stdio}, stdlib.malloc

import LibUV.*, LibUVConstants.*
import HTTP.RequestHandler
import ch03.httpClient.{HttpRequest, HttpResponse}

// when run, connect on browser http://localhost:8080 to see hello world.
@main
def run: Unit = serveHttp(8080, router)

var router: RequestHandler = _ => // just dummy for now
  HttpResponse(200, Map("Content-Length" -> "12"), "hello world\n")

def serveHttp(port: Int, handler: RequestHandler): Unit =
  println(s"about to serve on port ${port}")
  router = handler // keep router in mutable state, later bring back for onRead handler.
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

// about to serve on port 8080
// uv_ip4_addr returned 0
// uv_tcp_init(server) returned 0
// uv_tcp_bind returned 0
// uv_tcp_listen returned 0
// received connection
// uv_tcp_init(client) returned 0
// allocated data at 5eaa0420; assigning into handle storage at 5eaa0320
// uv_accept returned 0
// uv_read_start returned 0
// allocating 4096 bytes
// bytesRead: 16
// 2 bytes left
// bytesRead: 38
// 2 bytes left
// bytesRead: 122
// 2 bytes left
// bytesRead: 217
// 2 bytes left
// bytesRead: 250
// 2 bytes left
// bytesRead: 292
// 2 bytes left
// bytesRead: 300
// 2 bytes left
// bytesRead: 312
// 2 bytes left
// bytesRead: 336
// 2 bytes left
// bytesRead: 366
// 2 bytes left
// bytesRead: 392
// 2 bytes left
// bytesRead: 418
// 2 bytes left
// bytesRead: 440
// 2 bytes left
// bytesRead: 460
// 4 bytes left
// uv_write returned 0
// uv_shutdown returned 0
// write completed
// all pending writes complete, closing TCP connection
// uv_close returned 1551708544:
// Unknown system error 1551708544: Unknown system error 1551708544
// closed client connection
// received connection
// uv_tcp_init(client) returned 0
// allocated data at 5eaa0420; assigning into handle storage at 5eaa0320
// uv_accept returned 0
// uv_read_start returned 0
// allocating 4096 bytes
// bytesRead: 27
// 2 bytes left
// bytesRead: 49
// 2 bytes left
// bytesRead: 133
// 2 bytes left
// bytesRead: 168
// 2 bytes left
// bytesRead: 201
// 2 bytes left
// bytesRead: 243
// 2 bytes left
// bytesRead: 251
// 2 bytes left
// bytesRead: 263
// 2 bytes left
// bytesRead: 287
// 2 bytes left
// bytesRead: 320
// 2 bytes left
// bytesRead: 343
// 2 bytes left
// bytesRead: 368
// 2 bytes left
// bytesRead: 397
// 4 bytes left
// uv_write returned 0
// uv_shutdown returned 0
// write completed
// all pending writes complete, closing TCP connection
// uv_close returned 1551708544:
// Unknown system error 1551708544: Unknown system error 1551708544
// closed client connection
// Then I see hello world on browser!
// YAY!
