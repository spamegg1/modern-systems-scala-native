package ch05
package examples

import scalanative.unsafe.{stackalloc, Ptr, sizeof, CInt}
import scalanative.unsigned.{UShort, toUShort}
import scalanative.posix.sys.socket.{socket, bind, AF_INET, SOCK_STREAM, sockaddr}
import scalanative.posix.netinet.in.{sockaddr_in, INADDR_ANY}
import scalanative.posix.arpa.inet.htons // convert UShort from host to network byteorder

def bindExample(port: UShort): Unit =
  val serverSocket = socket(AF_INET, SOCK_STREAM, 0)

  val serverAddress = stackalloc[sockaddr_in](1)
  serverAddress._1 = AF_INET.toUShort // IP Socket
  serverAddress._2 = htons(port) // port
  serverAddress._3._1 = INADDR_ANY // bind to 0.0.0.0

  val serverSockAddr = serverAddress.asInstanceOf[Ptr[sockaddr]] // was .cast[]
  val addrSize = sizeof[sockaddr_in].toUInt // socklen_t = UInt

  // associate a socket fd with an address and port on host machine.
  // traditionally: "assign a name to a socket"
  //                       CInt        Ptr[sockaddr]  socklen_t
  val bindResult: CInt = bind(serverSocket, serverSockAddr, addrSize)
  println(s"bind returned $bindResult")

@main
def runBind = bindExample(65535.toUShort)

// $ scala-cli package . --main-class ch05.examples.runBind
// Wrote /home/spam/Projects/modern-systems-scala-native/ch05.examples.runBind, run it with
// ./ch05.examples.runBind
// $ ./ch05.examples.runBind
// bind returned 0
