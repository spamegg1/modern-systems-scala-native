package `03http`

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.*
import scalanative.libc.*
import stdio.*
import scalanative.posix.unistd.*
import scalanative.posix.sys.socket.*
import scalanative.posix.netinet.in.*
import scalanative.posix.arpa.inet.*
import collection.mutable
import scalanative.posix.netdb.*
import scalanative.posix.netdbOps.*

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

def writeRequestLine(
    socketFileDescriptor: Ptr[FILE],
    method: CString,
    uri: CString
): Unit =
  stdio.fprintf(socketFileDescriptor, c"%s %s %s\r\n", method, uri, c"HTTP/1.1")

def writeHeader(
    socketFileDescriptor: Ptr[FILE],
    key: CString,
    value: CString
): Unit =
  stdio.fprintf(socketFileDescriptor, c"%s: %s\r\n", key, value)

def writeBody(socketFileDescriptor: Ptr[FILE], body: CString): Unit =
  stdio.fputs(body, socketFileDescriptor)

def writeRequest(
    socketFileDescriptor: Ptr[FILE],
    request: HttpRequest
): Unit =
  Zone { // implicit z => // 0.5
    writeRequestLine(
      socketFileDescriptor,
      toCString(request.method),
      toCString(request.uri)
    )

    for (key, value) <- request.headers
    do writeHeader(socketFileDescriptor, toCString(key), toCString(value))

    stdio.fputs(c"\n", socketFileDescriptor)
    writeBody(socketFileDescriptor, toCString(request.body))
  }

def parseStatusLine(line: CString): Int =
  println("parsing status")
  val protocolPtr = stackalloc[Byte](64)
  val codePtr = stackalloc[Int](sizeof[Int])
  val descPtr = stackalloc[Byte](128)
  val scanResult =
    stdio.sscanf(line, c"%s %d %s\n", protocolPtr, codePtr, descPtr)

  if scanResult < 3 then throw new Exception("bad status line")
  else
    val code = !codePtr
    code

def parseHeaderLine(line: CString): (String, String) =
  val keyBuffer = stackalloc[Byte](64)
  val valueBuffer = stackalloc[Byte](64)
  stdio.printf(c"about to sscanf line: '%s'\n", line)

  val scanResult = stdio.sscanf(line, c"%s %s\n", keyBuffer, valueBuffer)
  if scanResult < 2 then throw new Exception("bad header line")
  else
    val keyString = fromCString(keyBuffer)
    val valueString = fromCString(valueBuffer)
    (keyString, valueString)

def readResponse(socketFileDescriptor: Ptr[FILE]): HttpResponse =
  val lineBuffer = stdlib.malloc(4096.toUSize) // 0.5
  println("reading status line?")

  var readResult = stdio.fgets(lineBuffer, 4096, socketFileDescriptor)
  val code = parseStatusLine(lineBuffer)
  var headers = mutable.Map[String, String]()

  println("reading first response header")
  readResult = stdio.fgets(lineBuffer, 4096, socketFileDescriptor)
  var lineLength = string.strlen(lineBuffer)

  while lineLength.toInt > 2
  do
    val (k, v) = parseHeaderLine(lineBuffer)
    println(s"${(k, v)}")
    headers(k) = v

    println("reading header")
    readResult = stdio.fgets(lineBuffer, 4096, socketFileDescriptor)
    lineLength = string.strlen(lineBuffer)

  val contentLength =
    if headers.contains("Content-Length:") then
      println("saw content-length")
      headers("Content-Length:").toInt
    else 65535

  val bodyBuffer = stdlib.malloc((contentLength + 1).toUSize) // 0.5
  val bodyReadResult =
    stdio.fread(
      bodyBuffer,
      1.toUSize,
      contentLength.toUSize,
      socketFileDescriptor
    )

  val bodyLength = string.strlen(bodyBuffer)
  if bodyLength.toULong != contentLength.toULong then
    println("""Warning: saw ${bodyLength} bytes, but expected
                      ${contentLength}""")

  val body = fromCString(bodyBuffer)
  HttpResponse(code, headers, body)

def makeConnection(address: CString, port: CString): Int =
  val hints = stackalloc[addrinfo](sizeof[addrinfo])
  string.memset(hints.asInstanceOf[Ptr[Byte]], 0, sizeof[addrinfo])
  hints.ai_family = AF_UNSPEC
  hints.ai_socktype = SOCK_STREAM
  val addrInfoPtr: Ptr[Ptr[addrinfo]] =
    stackalloc[Ptr[addrinfo]](sizeof[Ptr[addrinfo]])

  println("about to perform lookup")
  val lookupResult = getaddrinfo(address, port, hints, addrInfoPtr)
  println(s"lookup returned ${lookupResult}")

  if lookupResult != 0 then
    val errString = util.gai_strerror(lookupResult)
    stdio.printf(c"errno: %d %s\n", lookupResult, errString)
    throw new Exception("no address found")
  else
    val addrInfo = !addrInfoPtr
    stdio.printf(
      c"got addrinfo: flags %d, family %d, socktype %d, protocol %d\n",
      addrInfo.ai_family,
      addrInfo.ai_flags,
      addrInfo.ai_socktype,
      addrInfo.ai_protocol
    )

    println("creating socket")
    val sock =
      socket(addrInfo.ai_family, addrInfo.ai_socktype, addrInfo.ai_protocol)
    println(s"socket returned fd $sock")
    if sock < 0 then throw new Exception("error in creating socket")

    println("connecting")
    val connectResult = connect(sock, addrInfo.ai_addr, addrInfo.ai_addrlen)
    println(s"connect returned $connectResult")

    if connectResult != 0 then
      val err = errno.errno
      val errString = string.strerror(err)
      stdio.printf(c"errno: %d %s\n", err, errString)

      throw new Exception("connection failed")

    sock

def handleConnection(sock: Int, host: String, path: String): Unit =
  val socketFileDescriptor = util.fdopen(sock, c"r+")
  val headers = Map("Host" -> host)
  val req = HttpRequest("GET", path, headers, "")

  writeRequest(socketFileDescriptor, req)
  println("wrote request")
  fflush(socketFileDescriptor)

  val resp = readResponse(socketFileDescriptor)
  println(s"got Response: ${resp}")
  fclose(socketFileDescriptor)

// uncomment @main, make sure all other @main s in other files are commented
// use sbt> nativeLink to create executable
// run it in two Terminal windows with:
// nc -l -v 127.0.0.1 8081
// Listening on localhost 8081
// Connection received on localhost 58200
// GET / HTTP/1.1
// Host: 127.0.0.1
// ./target/scala-3.2.2/scala-native-out 127.0.0.1 8081 /
// looking up address: 127.0.0.1 port: 8081
// about to perform lookup
// lookup returned 0
// got addrinfo: flags 2, family 0, socktype 1, protocol 6
// creating socket
// socket returned fd 3
// connecting
// connect returned 0
// wrote request
// reading status line?
// also use with:
// ./target/scala-3.2.2/scala-native-out www.example.com 80 /
// @main
def httpClient(args: String*): Unit =
  if args.length != 3 then
    println(s"${args.length} {args}")
    println("Usage: ./tcp_test [address] [port] [path]")
    ()

  Zone { // implicit z => // 0.5
    val address = toCString(args(0))
    val host = args(0)
    val port = toCString(args(1))
    val path = args(2)
    stdio.printf(c"looking up address: %s port: %s\n", address, port)

    val sock = makeConnection(address, port)
    handleConnection(sock, host, path)
  }

@extern
object util:
  def getaddrinfo(
      address: CString,
      port: CString,
      hints: Ptr[addrinfo],
      res: Ptr[Ptr[addrinfo]]
  ): Int = extern
  def socket(family: Int, socktype: Int, protocol: Int): Int = extern
  def connect(sock: Int, addrInfo: Ptr[sockaddr], addrLen: CInt): Int = extern
  def gai_strerror(code: Int): CString = extern
  def fdopen(fd: Int, mode: CString): Ptr[FILE] = extern
