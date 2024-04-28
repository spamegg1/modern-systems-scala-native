package ch05
package httpServer

import scalanative.unsigned.{UnsignedRichInt, UInt, UShort}
import scalanative.unsafe.{Ptr, Zone, sizeof, CQuote}
import scalanative.libc.{stdio, stdlib, string, errno}, stdlib.malloc
import scalanative.posix.unistd
import scalanative.posix.sys.socket, socket.{sockaddr, AF_INET, SOCK_STREAM}
import scalanative.posix.netinet.in.{sockaddr_in, INADDR_ANY}
import scalanative.posix.arpa.inet

@main
def httpServer: Unit = serve(8080.toUShort)

def serve(port: UShort): Unit =
  // Allocate and initialize the server address
  val addrSize = sizeof[sockaddr_in]
  val serverAddress = malloc(addrSize).asInstanceOf[Ptr[sockaddr_in]]
  serverAddress._1 = AF_INET.toUShort // IP Socket
  serverAddress._2 = inet.htons(port) // port
  serverAddress._3._1 = inet.htonl(INADDR_ANY) // bind to 0.0.0.0

  // Bind and listen on a socket
  val sockFd = socket.socket(AF_INET, SOCK_STREAM, 0)
  val serverSockAddr = serverAddress.asInstanceOf[Ptr[sockaddr]]

  val bindResult = socket.bind(sockFd, serverSockAddr, addrSize.toUInt)
  println(s"bind returned $bindResult")

  val listenResult = socket.listen(sockFd, 128)
  println(s"listen returned $listenResult")

  val incoming = malloc(sizeof[sockaddr_in]).asInstanceOf[Ptr[sockaddr]]
  val incSz = malloc(sizeof[UInt]).asInstanceOf[Ptr[UInt]]
  !incSz = sizeof[sockaddr_in].toUInt
  println(s"accepting connections on port $port")

  while true do // Main accept() loop
    println(s"accepting")

    val connectionFd = socket.accept(sockFd, incoming, incSz)
    println(s"accept returned fd $connectionFd")

    if connectionFd <= 0 then
      val err = errno.errno
      val errString = string.strerror(err)
      stdio.printf(c"errno: %d %s\n", err, errString)

    handleConnection(connectionFd) // we'll replace this with forkAndHandle shortly
    unistd.close(connectionFd)

  unistd.close(sockFd)

def handleConnection(connSocket: Int, maxSize: Int = 1024): Unit =
  Parsing.parseRequest(connSocket) match
    case Some(request) =>
      val response = handleRequest(request)
      Parsing.writeResponse(connSocket, response)
    case None => ()

def handleRequest(request: HttpRequest): HttpResponse =
  val headers = Map("Content-type" -> "text/html")
  val body = s"received ${request.toString}\n"
  HttpResponse(200, headers, body)
