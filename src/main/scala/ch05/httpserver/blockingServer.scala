package ch05
package blockingServer

import scalanative.unsafe.{sizeof, Ptr}
import scalanative.unsigned.{UShort, toUShort}
import scalanative.libc.stdlib
import scalanative.posix.unistd
import scalanative.posix.sys.socket
import socket.{AF_INET, SOCK_STREAM, sockaddr}
import scalanative.posix.netinet.in.{sockaddr_in, INADDR_ANY}
import scalanative.posix.arpa.inet

def serve(port: UShort): Unit =
  val addrSize = sizeof[sockaddr_in] // Allocate and initialize the server address
  val serverAddress = stdlib.malloc(addrSize).asInstanceOf[Ptr[sockaddr_in]] // cast[...]
  // In the book this is never freed. The program will end before we can free it.
  // every modern OS will recover all the allocated memory space after a program exits.
  // In most cases, deallocating memory just before program exit is pointless.
  // OS will reclaim it anyway. Free will touch and page in dead objects; OS won't.
  // Be careful with "leak detectors" that count allocations. Some "leaks" are good!
  // There are many large projects which very intentionally do not free memory on exit
  // because it's a waste of time if you know your target platforms.

  !serverAddress.at1 = AF_INET.toUShort // IP Socket, was ._1
  !serverAddress.at2 = inet.htons(port) // port, was ._2
  !serverAddress.at3.at1 = INADDR_ANY // bind to 0.0.0.0, was ._3._1
  // needs to be cast back to Ptr[Byte] to be freed

  // Bind and listen on a socket
  val sockFd = socket.socket(AF_INET, SOCK_STREAM, 0)
  val serverSockAddr = serverAddress.asInstanceOf[Ptr[sockaddr]] // was .cast[...]

  val bindResult = socket.bind(sockFd, serverSockAddr, addrSize.toUInt)
  println(s"bind returned $bindResult")

  val listenResult = socket.listen(sockFd, 128)
  println(s"listen returned $listenResult")
  println(s"accepting connections on port $port")

  while true do // Main accept() loop. How do we ever exit this loop?
    val connectionFd = socket.accept(sockFd, null, null) // don't care for address / size
    println(s"accept returned fd $connectionFd")
    handleConnection(connectionFd) // we will replace this with forkAndHandle shortly

  // how do we ever reach here?
  unistd.close(sockFd) // do not leak file descriptors!
  stdlib.free(serverAddress) // should we? unneeded if needed for entire lifetime of prog

  // it's a waste of time when done right before exit().
  // It's like tidying a house before nuking it from orbit.
  // At exit, memory pages and swap space are simply released.
  // By contrast, a series of free() calls will burn CPU time.
  // But it's a good idea to free, if code will be used by others in larger program.
  // if it's a small amount of memory, freeing is not much of a waste.

@main
def simpleEchoServer = serve(65535.toUShort)

// compile with:
// $ scala-cli package . --main-class ch05.blockingServer.simpleEchoServer
// Wrote /home/spam/Projects/modern-systems-scala-native/project, run it with
// ./project
// $ ./project
// then on another Terminal, run netcat to connect to it:
// $ nc localhost 65535
//
// server:
// $ ./project
// bind returned 0
// listen returned 0
// accepting connections on port 65535
// accept returned fd 4
// read 48 bytes
// wrote 48 bytes
//
// netcat:
// $ nc localhost 65535
// Connection accepted!  Enter a message and it will be echoed back
// here is a message from netcat, should be echoed
// here is a message from netcat, should be echoed

// If you open a third Terminal window and try to connect, it connects, but waits.
// Because we are blocking. So it waits for the first connection to stop.
