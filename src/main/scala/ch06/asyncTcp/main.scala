package ch06
package asyncTcp

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.{CQuote, stackalloc, CString, CSize, CSSize, Ptr, sizeof}
import scalanative.unsafe.{CStruct3, CFuncPtr1, CFuncPtr2, CFuncPtr3}
import scalanative.libc.{stdio, stdlib, string}

import LibUV.*, LibUVConstants.*

@main
def run: Unit =
  println("hello!")
  serveTcp(c"0.0.0.0", 8080, 0, 100, connectionCB)
  println("done?")

// cannot be factored out due to readCB differences.
val connectionCB = CFuncPtr2.fromScalaFunction[TCPHandle, Int, Unit]:
  (handle: TCPHandle, status: Int) =>
    println("received connection")

    // initialize the new client tcp handle and its state
    val client = stdlib.malloc(uv_handle_size(UV_TCP_T)).asInstanceOf[TCPHandle]
    checkError(uv_tcp_init(loop, client), "uv_tcp_init(client)")

    // this line shows that TCPHandle has to be Ptr[Ptr[Byte]] not Ptr[Byte]
    // because !client itself is another pointer
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

    if size < 0 then // client terminated connection unprompted.
      sendResponse(client, clientStatePtr) // echo back everything read, at once.
      println("connection is closed, shutting down")
      shutdown(client)
    else // instead of echoing back line-by-line, keep reading until completion.
      appendData(clientStatePtr, size, buffer)
      stdlib.free(buffer._1)

// compile with:
// $ scala-cli package . --main-class ch06.asyncTcp.run
// ...
// Wrote /home/spam/Projects/modern-systems-scala-native/project, run it with
// ./project
// Run it with:
// $ ./project
// In another Terminal, run Netcat to connect, then type some stuff:
// $ nc localhost 8080

// Here are the outputs:
// $ nc localhost 8080
// spam
// Ctrl+C

// $ ./project
// hello!
// uv_ip4_addr returned 0
// uv_tcp_init(server) returned 0
// uv_tcp_bind returned 0
// uv_tcp_listen returned 0
// received connection
// uv_tcp_init(client) returned 0
// allocated data at 70fa5420; assigning into handle storage at 70fa5320
// uv_accept returned 0
// uv_read_start returned 0
// allocating 4096 bytes
// read 5 bytes
// client 70fa5420: 5/4096 bytes used
// allocating 4096 bytes
// read -4095 bytes
// uv_write returned 0
// connection is closed, shutting down
// uv_shutdown returned 0
// write completed
// all pending writes complete, closing TCP connection
// uv_close returned 1684660608: Unknown system error 1684660608: Unknown system error 1684660608
// closed client connection
// THESE ERRORS ARE COMPLETELY NORMAL.
