package ch09
package lmdbWeb

import scalanative.unsigned.UnsignedRichLong
import scalanative.unsafe.*
import scalanative.libc.{stdlib, string}, stdlib.malloc

object Server:
  import LibUV.*, ch07.LibUVConstants.*
  import ch06.asyncHttp.HTTP, HTTP.RequestHandler
  import ch03.httpClient.{HttpRequest, HttpResponse}

  type ClientState = CStruct3[Ptr[Byte], CSize, CSize]

  val loop = uv_default_loop()

  val connectionCB = CFuncPtr2.fromScalaFunction[TCPHandle, Int, Unit]:
    (handle: TCPHandle, status: Int) =>
      println("received connection")
      val client = malloc(uv_handle_size(UV_TCP_T)).asInstanceOf[TCPHandle]
      checkError(uv_tcp_init(loop, client), "uv_tcp_init(client)")
      checkError(uv_accept(handle, client), "uv_accept")
      checkError(uv_read_start(client, allocCB, readCB), "uv_read_start")

  var router: RequestHandler = _ =>
    HttpResponse(200, Map("Content-Length" -> "12"), "hello world\n")

  def serveHttp(port: Int, handler: RequestHandler): Unit =
    println(s"about to serve on port ${port}")
    router = handler
    serveTcp(c"0.0.0.0", port, 0, 4096, connectionCB)

  def serveTcp(
      address: CString,
      port: Int,
      flags: Int,
      backlog: Int,
      callback: ConnectionCB
  ): Unit =
    val addr = stackalloc[Byte](1)
    val addrConvert = uv_ip4_addr(address, port, addr)
    val handle = malloc(uv_handle_size(UV_TCP_T)).asInstanceOf[TCPHandle]
    checkError(uv_tcp_init(loop, handle), "uv_tcp_init(server)")
    checkError(uv_tcp_bind(handle, addr, flags), "uv_tcp_bind")
    checkError(uv_listen(handle, backlog, callback), "uv_tcp_listen")
    uv_run(loop, UV_RUN_DEFAULT)

  val allocCB = CFuncPtr3.fromScalaFunction[TCPHandle, CSize, Ptr[Buffer], Unit]:
    (client: TCPHandle, size: CSize, buffer: Ptr[Buffer]) =>
      val buf = malloc(4096) // 0.5
      buffer._1 = buf
      buffer._2 = 4096.toUSize // 0.5

  def appendData(state: Ptr[ClientState], size: CSSize, buffer: Ptr[Buffer]): Unit =
    val copyPosition: Ptr[Byte] = state._1 + state._3
    string.strncpy(copyPosition, buffer._1, size.toUSize) // 0.5
    state._3 = state._3 + size.toUSize // 0.5

  def sendResponse(client: TCPHandle, response: HttpResponse): Unit =
    val req = malloc(uv_req_size(UV_WRITE_REQ_T)).asInstanceOf[WriteReq]
    val responseBuffer = malloc(sizeof[Buffer]).asInstanceOf[Ptr[Buffer]]

    responseBuffer._1 = malloc(4096) // 0.5
    responseBuffer._2 = 4096.toUSize // 0.5
    HTTP.makeResponse(response, responseBuffer)

    responseBuffer._2 = string.strlen(responseBuffer._1)
    !req = responseBuffer.asInstanceOf[Ptr[Byte]]
    checkError(uv_write(req, client, responseBuffer, 1, writeCB), "uv_write")

  def shutdown(client: TCPHandle): Unit =
    val shutdownReq = malloc(uv_req_size(UV_SHUTDOWN_REQ_T)).asInstanceOf[ShutdownReq]
    !shutdownReq = client.asInstanceOf[Ptr[Byte]]
    checkError(uv_shutdown(shutdownReq, client, shutdownCB), "uv_shutdown")

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

  val writeCB = CFuncPtr2.fromScalaFunction[WriteReq, Int, Unit]:
    (writeReq: WriteReq, status: Int) =>
      println("write completed")
      val responseBuffer = (!writeReq).asInstanceOf[Ptr[Buffer]]
      stdlib.free(responseBuffer._1)
      stdlib.free(responseBuffer.asInstanceOf[Ptr[Byte]])
      stdlib.free(writeReq.asInstanceOf[Ptr[Byte]])

  val shutdownCB = CFuncPtr2.fromScalaFunction[ShutdownReq, Int, Unit]:
    (shutdownReq: ShutdownReq, status: Int) =>
      println("all pending writes complete, closing TCP connection")
      val client = (!shutdownReq).asInstanceOf[TCPHandle]
      checkError(uv_close(client, closeCB), "uv_close")
      uv_close(client, closeCB)
      stdlib.free(shutdownReq.asInstanceOf[Ptr[Byte]])

  val closeCB = CFuncPtr1.fromScalaFunction[TCPHandle, Unit]: (client: TCPHandle) =>
    stdlib.free(client.asInstanceOf[Ptr[Byte]])
    println("closed client connection")
