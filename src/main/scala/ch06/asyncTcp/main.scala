package ch06
package asyncTcp

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.*
import scalanative.libc.*
import stdlib.*

import LibUV.*, LibUVConstants.*

type ClientState = CStruct3[Ptr[Byte], CSize, CSize]

@main
def asyncTcp: Unit =
  println("hello!")
  serveTcp(c"0.0.0.0", 8080, 0, 100, connectionCB)
  println("done?")

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
    clientStatePtr = initialize_client_state(client)

    // accept the incoming connection into the new handle
    checkError(uv_accept(handle, client), "uv_accept")

    // set up callbacks for incoming data
    checkError(uv_read_start(client, allocCB, readCB), "uv_read_start")

def initialize_client_state(client: TCPHandle): Ptr[ClientState] =
  val clientStatePtr = stdlib.malloc(sizeof[ClientState]).asInstanceOf[Ptr[ClientState]]
  stdio.printf(
    c"allocated data at %x; assigning into handle storage at %x\n",
    clientStatePtr,
    client
  )
  val client_state_data = stdlib.malloc(4096.toUSize) // 0.5
  clientStatePtr._1 = client_state_data
  clientStatePtr._2 = 4096.toUSize // total  // 0.5
  clientStatePtr._3 = 0.toUSize // used  // 0.5
  !client = clientStatePtr.asInstanceOf[Ptr[Byte]]
  clientStatePtr

val allocCB = CFuncPtr3.fromScalaFunction[TCPHandle, CSize, Ptr[Buffer], Unit]:
  (client: TCPHandle, size: CSize, buffer: Ptr[Buffer]) =>
    println("allocating 4096 bytes")
    val buf = stdlib.malloc(4096.toUSize) // 0.5
    buffer._1 = buf
    buffer._2 = 4096.toUSize // 0.5

val readCB = CFuncPtr3.fromScalaFunction[TCPHandle, CSSize, Ptr[Buffer], Unit]:
  (client: TCPHandle, size: CSSize, buffer: Ptr[Buffer]) =>
    println(s"read $size bytes")
    var clientStatePtr = (!client).asInstanceOf[Ptr[ClientState]]

    if size < 0 then
      sendResponse(client, clientStatePtr)
      println("connection is closed, shutting down")
      shutdown(client)
    else
      appendData(clientStatePtr, size, buffer)
      stdlib.free(buffer._1)

def appendData(
    state: Ptr[ClientState],
    size: CSSize,
    buffer: Ptr[Buffer]
): Unit =
  val copy_position = state._1 + state._3
  string.strncpy(copy_position, buffer._1, size.toUSize) // 0.5
  // be sure to update the length of the data since we have copied into it
  state._3 = state._3 + size.toUSize // 0.5
  stdio.printf(c"client %x: %d/%d bytes used\n", state, state._3, state._2)

def sendResponse(client: TCPHandle, state: Ptr[ClientState]): Unit =
  val resp = malloc(uv_req_size(UV_WRITE_REQ_T)).asInstanceOf[WriteReq]
  val resp_buffer = malloc(sizeof[Buffer]).asInstanceOf[Ptr[Buffer]]
  resp_buffer._1 = make_response(state)
  resp_buffer._2 = string.strlen(resp_buffer._1)
  !resp = resp_buffer.asInstanceOf[Ptr[Byte]]
  checkError(uv_write(resp, client, resp_buffer, 1, writeCB), "uv_write")

def make_response(state: Ptr[ClientState]): CString =
  val response_format = c"received response:\n%s\n"
  val response_data = malloc(string.strlen(response_format) + state._3)
  stdio.sprintf(response_data, response_format, state._1)
  response_data

val writeCB = CFuncPtr2.fromScalaFunction[WriteReq, Int, Unit]:
  (writeReq: WriteReq, status: Int) =>
    println("write completed")
    val resp_buffer = (!writeReq).asInstanceOf[Ptr[Buffer]]
    stdlib.free(resp_buffer._1)
    stdlib.free(resp_buffer.asInstanceOf[Ptr[Byte]])
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
