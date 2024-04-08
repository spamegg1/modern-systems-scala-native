package ch05
package examples

import scalanative.unsafe.{stackalloc, Ptr, sizeof}
import scalanative.unsigned.{UShort, toUShort}
import scalanative.posix.sys.socket.{socket, bind, AF_INET, SOCK_STREAM, sockaddr}
import scalanative.posix.netinet.in.{sockaddr_in, INADDR_ANY}
import scalanative.posix.arpa.inet.htons

def bindExample(port: UShort): Unit =
  val serverSocket = socket(AF_INET, SOCK_STREAM, 0)

  val serverAddress = stackalloc[sockaddr_in](1)
  serverAddress._1 = AF_INET.toUShort // IP Socket
  serverAddress._2 = htons(port) // port
  serverAddress._3._1 = INADDR_ANY // bind to 0.0.0.0

  val serverSockAddr = serverAddress.asInstanceOf[Ptr[sockaddr]]
  val addrSize = sizeof[sockaddr_in].toUInt

  val bindResult = bind(serverSocket, serverSockAddr, addrSize)
  println(s"bind returned $bindResult")
