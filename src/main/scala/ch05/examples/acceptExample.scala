package ch05
package examples

import scalanative.unsafe.{stackalloc, CInt, Ptr, sizeof}
import scalanative.unsigned.UInt
import scalanative.posix.sys.socket.{accept, sockaddr}
import scalanative.posix.netinet.in.sockaddr_in

def acceptExample(serverSocket: CInt): Unit = // socket has to be listening already.
  val clientAddress = stackalloc[sockaddr_in](1) // Ptr[Byte]
  val clientSockAddr = clientAddress.asInstanceOf[Ptr[sockaddr]] // was .cast[???]
  val clientAddrSize = stackalloc[UInt](1)
  !clientAddrSize = sizeof[sockaddr_in].toUInt

  // def accept(
  //   socket: CInt,               // should have called bind() and listen() first
  //   address: Ptr[sockaddr],     // allocated but uninitialized, will be set on success
  //   address_len: Ptr[socklen_t] // same as size (UInt) of socket address, but pointer.
  // ): CInt = extern              // new connected socket fd different than listening fd

  // this returns a completely distinct socket fd from the listening serverSocket fd
  // it also sets clientSockAddr to the address of client who just connected to us
  val connectionSocket = accept(serverSocket, clientSockAddr, clientAddrSize)

  println(
    s"""accept returned fd $connectionSocket;
       |connected client address is
       |${formatSockaddrIn(clientAddress)}""".stripMargin
  )

def formatSockaddrIn(clientAddress: Ptr[sockaddr_in]): String = ???
