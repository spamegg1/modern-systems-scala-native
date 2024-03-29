package ch05.httpServer

import scalanative.unsigned.{UnsignedRichInt, UInt, UShort}
import scalanative.unsafe.*
import scalanative.libc.*
import stdio.{FILE, fclose}
import stdlib.malloc
import scalanative.posix.unistd.*
import scalanative.posix.sys.socket.*
import scalanative.posix.netinet.in.*
import scalanative.posix.arpa.inet.*
import collection.mutable

case class HttpRequest(
    method: String,
    uri: String,
    headers: collection.Map[String, String],
    body: String
)

case class HttpResponse(
    code: Int,
    headers: collection.Map[String, String],
    body: String
)

object Parsing:
  def parseHeaderLine(line: CString): (String, String) =
    val keyBuffer = stackalloc[Byte](64)
    val valueBuffer = stackalloc[Byte](64)
    val scanResult = stdio.sscanf(line, c"%s %s\n", keyBuffer, valueBuffer)

    if scanResult < 2 then throw Exception("bad header line")
    else
      val keyString = fromCString(keyBuffer)
      val valueString = fromCString(valueBuffer)
      (keyString, valueString)

  def parseRequestLine(line: CString): (String, String) =
    val methodBuffer = stackalloc[Byte](16)
    val urlBuffer = stackalloc[Byte](1024)
    val protocolBuffer = stackalloc[Byte](32)
    val scanResult = stdio.sscanf(
      line,
      c"%s %s %s\n",
      methodBuffer,
      urlBuffer,
      protocolBuffer
    )
    if scanResult < 3 then throw Exception("bad request line")
    else
      val method = fromCString(methodBuffer)
      val url = fromCString(urlBuffer)
      (method, url)

  def parseRequest(conn: Int): Option[HttpRequest] =
    val socketFd = util.fdopen(conn, c"r")
    val lineBuffer = stdlib.malloc(4096.toUSize) // fix // 0.5
    var readResult = stdio.fgets(lineBuffer, 4096, socketFd)

    val (method, url) = parseRequestLine(lineBuffer)
    stdio.printf(c"read request line: %s", lineBuffer)
    println(s"${(method, url)}")

    var headers = mutable.Map[String, String]()
    readResult = stdio.fgets(lineBuffer, 4096, socketFd)

    var lineLength = string.strlen(lineBuffer)
    while lineLength > 2.toULong do
      val (k, v) = parseHeaderLine(lineBuffer)
      headers(k) = v
      readResult = stdio.fgets(lineBuffer, 4096, socketFd)
      lineLength = string.strlen(lineBuffer)

    Some(HttpRequest(method, url, headers, ""))

  def writeResponse(conn: Int, resp: HttpResponse): Unit =
    val socketFd = util.fdopen(conn, c"r+")
    Zone { // implicit z => // 0.5
      stdio.fprintf(socketFd, c"%s %s %s\r\n", c"HTTP/1.1", c"200", c"OK")

      for (k, v) <- resp.headers
      do stdio.fprintf(socketFd, c"%s: %s\r\n", toCString(k), toCString(v))

      stdio.fprintf(socketFd, c"\r\n")
      stdio.fprintf(socketFd, toCString(resp.body))
    }
    fclose(socketFd)

@main
def httpServer05(args: String*): Unit = serve(8082.toUShort)

def serve(port: UShort): Unit =
  // Allocate and initialize the server address
  val addrSize = sizeof[sockaddr_in]
  val serverAddress = malloc(addrSize).asInstanceOf[Ptr[sockaddr_in]]
  serverAddress._1 = AF_INET.toUShort // IP Socket
  serverAddress._2 = htons(port) // port
  serverAddress._3._1 = htonl(INADDR_ANY) // bind to 0.0.0.0

  // Bind and listen on a socket
  val sockFd = socket(AF_INET, SOCK_STREAM, 0)
  val serverSockAddr = serverAddress.asInstanceOf[Ptr[sockaddr]]
  val bindResult = bind(sockFd, serverSockAddr, addrSize.toUInt)
  println(s"bind returned $bindResult")
  val listenResult = listen(sockFd, 128)
  println(s"listen returned $listenResult")

  val incoming = malloc(sizeof[sockaddr_in]).asInstanceOf[Ptr[sockaddr]]
  val incSz = malloc(sizeof[UInt]).asInstanceOf[Ptr[UInt]]
  !incSz = sizeof[sockaddr_in].toUInt
  println(s"accepting connections on port $port")

  // Main accept() loop
  while true do
    println(s"accepting")
    val connectionFd = accept(sockFd, incoming, incSz)
    println(s"accept returned fd $connectionFd")

    if connectionFd <= 0 then
      val err = errno.errno
      val errString = string.strerror(err)
      stdio.printf(c"errno: %d %s\n", err, errString)

    // we will replace handleConnection with fork_and_handle shortly
    handleConnection(connectionFd)
    close(connectionFd)

  close(sockFd)

import Parsing.*, scala.util.boundary, boundary.break

def handleConnection(connSocket: Int, maxSize: Int = 1024): Unit =
  boundary:
    while true do
      parseRequest(connSocket) match
        case Some(request) =>
          val response = handleRequest(request)
          writeResponse(connSocket, response)
          break() // replaced return
        case None => break() // replaced return

def handleRequest(request: HttpRequest): HttpResponse =
  val headers = Map("Content-type" -> "text/html")
  val body = s"received ${request.toString}\n"
  HttpResponse(200, headers, body)

@extern
object util:
  def fdopen(fd: Int, mode: CString): Ptr[FILE] = extern
