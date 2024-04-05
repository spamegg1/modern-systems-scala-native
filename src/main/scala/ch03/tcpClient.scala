package ch03.tcp

import scalanative.unsafe.{Zone, Ptr, toCString, fromCString, CQuote, CString}
import scalanative.libc.{stdio, stdlib}
import stdio.{FILE, fgets, fclose}

import ch03.common
import common.util

// used in handleConnection
def makeRequest(sock: Ptr[FILE], request: String): String =
  // sending request and receiving response thru socket is low-level (C), so allocate.
  // 4096 bytes is a good size for our response string.
  val responseBuffer = stdlib.malloc(4096) // 0.5

  val response: String = Zone: // implicit z => // 0.5, we don't need implicit z anymore
    val requestCstring: CString = toCString(request) // requires a Zone
    stdio.fprintf(sock, requestCstring) // write request to socket stream, send

    // receive response from server through same socket stream (bidirectional)
    val responseCstring = fgets(responseBuffer, 4096 - 1, sock) // 4096 is an OK size.
    fromCString(responseBuffer) // does not require a Zone

  stdlib.free(responseBuffer)
  response

// used in main
def handleConnection(sock: Int): Unit =
  val socketFileDescriptor: Ptr[FILE] = util.fdopen(sock, c"r+") // convert socket to FILE

  val resp: String = makeRequest(socketFileDescriptor, "hello? is anybody there?\n")
  println(s"I got a response: ${resp.trim()}")

  fclose(socketFileDescriptor)
  println("done")

// use netcat in one terminal session: nc -l -v 127.0.0.1 8080
// use netcat in second terminal: nc -v 127.0.0.1 8080
// first terminal:
// nc -l -v 127.0.0.1 8080
// Listening on localhost 8080
// Connection received on localhost 33220
// asd
// text from here is copied over there!
// what about the other direction?
// Yep, it's bi-directional.
// second terminal:
// nc -v 127.0.0.1 8080
// Connection to 127.0.0.1 8080 port [tcp/http-alt] succeeded!
// asd
// text from here is copied over there!
// what about the other direction?
// Yep, it's bi-directional.

// Compile with: scala-cli 01tcpClient.scala --native --native-version 0.5.0-RC2
// On the server-side:
// nc -l -v 127.0.0.1 8080
// Listening on localhost 8080
// Connection received on localhost 48390
// hello? is anybody there?
// yep!
// On the client-side:
// ./.scala-build/Templates_25019ee6b9-c9c053f2dd/native/main 127.0.0.1 8080
// looking up address: 127.0.0.1 port: 8080
// about to perform lookup
// lookup returned 0
// got addrinfo: flags 2, family 0, socktype 1, protocol 6
// creating socket
// socket returned fd 3
// connecting
// connect returned 0
// I got a response: yep!
// done
@main
def tcpClient(args: String*): Unit =
  if args.length != 2 then
    println("Usage: ./tcp_test [address] [port]")
    () // exit
  else
    val sock = Zone: // implicit z => // 0.5
      val (address, port) = (toCString(args(0)), toCString(args(1))) // requires Zone
      stdio.printf(c"looking up address: %s port: %s\n", address, port)
      common.makeConnection(address, port) // looks up address, returns socket
    handleConnection(sock) // gets response from socket and prints it.
