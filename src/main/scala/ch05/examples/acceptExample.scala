package ch05
package examples

import scalanative.unsafe.{stackalloc, CInt, Ptr, sizeof}
import scalanative.unsigned.UInt
import scalanative.posix.sys.socket.{accept, sockaddr}
import scalanative.posix.netinet.in.sockaddr_in

def acceptExample(serverSocket: CInt): Unit =
  val clientAddress = stackalloc[sockaddr_in](1)
  val clientSockAddr = clientAddress.asInstanceOf[Ptr[sockaddr]] // was .cast[???]
  val clientAddrSize = stackalloc[UInt](1)
  !clientAddrSize = sizeof[sockaddr_in].toUInt

  val connectionSocket = accept(serverSocket, clientSockAddr, clientAddrSize)
  println(
    s"""accept returned fd $connectionSocket;
      |connected client address is
      |${formatSockaddrIn(clientAddress)}""".stripMargin
  )

def formatSockaddrIn(clientAddress: Ptr[sockaddr_in]): String = ???
