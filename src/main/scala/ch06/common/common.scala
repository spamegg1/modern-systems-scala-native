package ch06

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.{Ptr, CString, CSize, CSSize, stackalloc, sizeof, CQuote}
import scalanative.unsafe.{CStruct3, CFuncPtr1, CFuncPtr2, CFuncPtr3}
import scalanative.libc.{stdio, stdlib, string}, stdlib.malloc, string.strlen
import LibUV.*, LibUVConstants.*

type ClientState = CStruct3[Ptr[Byte], CSize, CSize] // buffer, allocated, used
val loop = uv_default_loop()

def serveTcp( // main server function
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

// Utilities / helpers:

// used in connection callback
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

def makeResponse(state: Ptr[ClientState]): CString =
  val responseFormat = c"received response:\n%s\n"
  val responseData = malloc(strlen(responseFormat) + state._3) // buffer to write to
  stdio.sprintf(responseData, responseFormat, state._1) // write clientState to buffer
  responseData // return buffer. type pun: Ptr[Byte] = Ptr[CChar] = CString

// writes are tracked individually rather than per connection.
def sendResponse(client: TCPHandle, state: Ptr[ClientState]): Unit = // used in readCB
  // In the book, the type pun is WriteReq instead of Ptr[Buffer]. Which one should it be?
  // WriteReq = Ptr[Ptr[Byte]], but Ptr[Buffer] = Ptr[CStruct2[Ptr[Byte], CSize]]
  // very different! Is Ptr[Byte] = CStruct2[Ptr[Byte], CSize]? I suppose yes!
  val responseBuffer = malloc(sizeof[Buffer]).asInstanceOf[Ptr[Buffer]]
  responseBuffer._1 = makeResponse(state)
  responseBuffer._2 = string.strlen(responseBuffer._1)

  val req = malloc(uv_req_size(UV_WRITE_REQ_T)).asInstanceOf[WriteReq]
  !req = responseBuffer.asInstanceOf[Ptr[Byte]]

  checkError(uv_write(req, client, responseBuffer, 1, writeCB), "uv_write")

// Used in readCB. Append data from buffer to client state. Then buffer is freed.
def appendData(state: Ptr[ClientState], size: CSSize, buffer: Ptr[Buffer]): Unit =
  val copyPosition = state._1 + state._3 // beginning + offset (bytes used so far)
  string.strncpy(copyPosition, buffer._1, size.toUSize) // 0.5

  // be sure to update the length of the data since we have copied into it
  state._3 = state._3 + size.toUSize // 0.5
  stdio.printf(c"client %x: %d/%d bytes used\n", state, state._3, state._2)

def shutdown(client: TCPHandle): Unit =
  val shutdownReq = malloc(uv_req_size(UV_SHUTDOWN_REQ_T)).asInstanceOf[ShutdownReq]
  !shutdownReq = client.asInstanceOf[Ptr[Byte]]
  checkError(uv_shutdown(shutdownReq, client, shutdownCB), "uv_shutdown")

// Callbacks:

// Used as buffer for reading / writing data over TCP connection.
// We are responsible for giving the loop a buffer to safely read data into.
// We disregard the size. libuv normally requests 65536 but we'll use 4096
// (because it's a single line echo server, 65536 is overkill).
// If more space is needed, the loop can call this as many times as it needs.
val allocCB = CFuncPtr3.fromScalaFunction[TCPHandle, CSize, Ptr[Buffer], Unit]:
  (client: TCPHandle, size: CSize, buffer: Ptr[Buffer]) =>
    println("allocating 4096 bytes") // disregard size
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
