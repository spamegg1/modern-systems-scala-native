package ch05.blockingServer

import scalanative.unsafe.{sizeof, Ptr, CQuote}
import scalanative.unsigned.{UShort, toUShort, toCSize}
import scalanative.libc.stdlib.malloc
import scalanative.libc.stdio.EOF
import scalanative.libc.string.strlen
import scalanative.posix.unistd
import scalanative.posix.sys.socket
import socket.{AF_INET, SOCK_STREAM, sockaddr}
import scalanative.posix.netinet.in.{sockaddr_in, INADDR_ANY}
import scalanative.posix.arpa.inet

def serve(port: UShort): Unit =
  // Allocate and initialize the server address
  val addrSize = sizeof[sockaddr_in]
  val serverAddress = malloc(addrSize).asInstanceOf[Ptr[sockaddr_in]] // 0.5: .cast[...]
  !serverAddress.at1 = AF_INET.toUShort // IP Socket // 0.5: ._1
  !serverAddress.at2 = inet.htons(port) // port // 0.5: ._2
  !serverAddress.at3.at1 = INADDR_ANY // bind to 0.0.0.0 // 0.5: ._3._1
  // needs to be cast back to Ptr[Byte] to be freed,

  // Bind and listen on a socket
  val sockFd = socket.socket(AF_INET, SOCK_STREAM, 0)
  val serverSockAddr = serverAddress.asInstanceOf[Ptr[sockaddr]] // .cast[...]
  val bindResult = socket.bind(sockFd, serverSockAddr, addrSize.toUInt)
  println(s"bind returned $bindResult")

  val listenResult = socket.listen(sockFd, 128)
  println(s"listen returned $listenResult")
  println(s"accepting connections on port $port")

  // Main accept() loop
  while true do
    val connectionFd = socket.accept(sockFd, null, null)
    println(s"accept returned fd $connectionFd")
    // we will replace handleConnection with fork_and_handle shortly
    handleConnection(connectionFd)

  unistd.close(sockFd)

def handleConnection(connSocket: Int, maxSize: Int = 1024): Unit =
  import scala.util.boundary, boundary.break

  val message = c"Connection accepted!  Enter a message and it will be echoed back\n"
  val promptWrite = unistd.write(connSocket, message, strlen(message))
  val lineBuffer = malloc(maxSize) // this is never freed, program ends before it can be.

  boundary:
    while true do
      val bytesRead = unistd.read(connSocket, lineBuffer, maxSize.toCSize) // 0.5
      println(s"read $bytesRead bytes")

      if bytesRead == EOF then break() //  connection has been closed by the client

      val bytesWritten = unistd.write(connSocket, lineBuffer, bytesRead.toCSize) // 0.5
      println(s"wrote $bytesWritten bytes")
