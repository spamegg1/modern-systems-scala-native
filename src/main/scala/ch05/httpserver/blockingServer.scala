package ch05
package blockingServer

import scalanative.unsafe.{sizeof, Ptr}
import scalanative.unsigned.{UShort, toUShort}
import scalanative.libc.stdlib.malloc
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

  while true do // Main accept() loop
    val connectionFd = socket.accept(sockFd, null, null)
    println(s"accept returned fd $connectionFd")
    handleConnection(connectionFd) // we will replace this with forkAndHandle shortly

  unistd.close(sockFd)
