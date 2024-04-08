package ch06
package asyncTcp

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.{CQuote, stackalloc, CString, CSize, CSSize, Ptr, sizeof}
import scalanative.unsafe.{CStruct3, CFuncPtr1, CFuncPtr2, CFuncPtr3}
import scalanative.libc.{stdio, stdlib, string}
import stdlib.malloc

import LibUV.*, LibUVConstants.*

@main
def asyncTcp: Unit =
  println("hello!")
  serveTcp(c"0.0.0.0", 8080, 0, 100, connectionCB)
  println("done?")

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
    println(s"read $size bytes")
    var clientStatePtr = (!client).asInstanceOf[Ptr[ClientState]]

    if size < 0 then
      sendResponse(client, clientStatePtr)
      println("connection is closed, shutting down")
      shutdown(client)
    else
      appendData(clientStatePtr, size, buffer)
      stdlib.free(buffer._1)
