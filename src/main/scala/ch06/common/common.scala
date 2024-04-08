package ch06

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.{Ptr, CString, CSize, CSSize, stackalloc, sizeof, CQuote}
import scalanative.unsafe.{CStruct3, CFuncPtr1, CFuncPtr2, CFuncPtr3}
import scalanative.libc.{stdio, stdlib, string}, stdlib.malloc, string.strlen
import LibUV.*
import LibUVConstants.*

type ClientState = CStruct3[Ptr[Byte], CSize, CSize]

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

def initializeClientState(client: TCPHandle): Ptr[ClientState] =
  val clientStatePtr = malloc(sizeof[ClientState]).asInstanceOf[Ptr[ClientState]]

  stdio.printf(
    c"allocated data at %x; assigning into handle storage at %x\n",
    clientStatePtr,
    client
  )

  val clientStateData = malloc(4096) // 0.5
  clientStatePtr._1 = clientStateData
  clientStatePtr._2 = 4096.toUSize // total  // 0.5
  clientStatePtr._3 = 0.toUSize // used  // 0.5
  !client = clientStatePtr.asInstanceOf[Ptr[Byte]]
  clientStatePtr

val allocCB = CFuncPtr3.fromScalaFunction[TCPHandle, CSize, Ptr[Buffer], Unit]:
  (client: TCPHandle, size: CSize, buffer: Ptr[Buffer]) =>
    println("allocating 4096 bytes")
    val buf = malloc(4096) // 0.5
    buffer._1 = buf
    buffer._2 = 4096.toUSize // 0.5

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

def makeResponse(state: Ptr[ClientState]): CString =
  val response_format = c"received response:\n%s\n"
  val response_data = malloc(strlen(response_format) + state._3)
  stdio.sprintf(response_data, response_format, state._1)
  response_data

def sendResponse(client: TCPHandle, state: Ptr[ClientState]): Unit =
  val req = malloc(uv_req_size(UV_WRITE_REQ_T)).asInstanceOf[WriteReq]
  val responseBuffer = malloc(sizeof[Buffer]).asInstanceOf[Ptr[Buffer]]
  responseBuffer._1 = makeResponse(state)
  responseBuffer._2 = string.strlen(responseBuffer._1)
  !req = responseBuffer.asInstanceOf[Ptr[Byte]]
  checkError(uv_write(req, client, responseBuffer, 1, writeCB), "uv_write")

def appendData(state: Ptr[ClientState], size: CSSize, buffer: Ptr[Buffer]): Unit =
  val copyPosition = state._1 + state._3
  string.strncpy(copyPosition, buffer._1, size.toUSize) // 0.5
  // be sure to update the length of the data since we have copied into it
  state._3 = state._3 + size.toUSize // 0.5
  stdio.printf(c"client %x: %d/%d bytes used\n", state, state._3, state._2)
