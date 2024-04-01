package ch03.http

import scalanative.posix.sys.socket.{AF_UNSPEC, SOCK_STREAM, socket, sockaddr, connect}
import scalanative.posix.netdb.{addrinfo, getaddrinfo}
import scalanative.posix.netdbOps.addrinfoOps // ai_family, ai_socktype
import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.{Zone, Ptr, CInt, CString, toCString, fromCString, CQuote}
import scalanative.unsafe.{stackalloc, sizeof, extern}
import scalanative.libc.{stdio, stdlib, string, errno}
import stdio.{FILE, fgets, fclose, fflush}

import collection.mutable.{Map => MMap}

// These are Scala strings, they will be converted to C-strings later.
case class HttpRequest(
    method: String, // GET, POST, PUT, etc.
    uri: String, // e.g. www.pragprog.com
    headers: Map[String, String], // immutable
    body: String
)
case class HttpResponse(
    code: Int, // 200, 301, 404, 500, etc.
    headers: MMap[String, String], // mutable
    body: String
)

// Example HTTP request line: GET /index.html HTTP/1.1
def writeRequestLine(
    socketFileDescriptor: Ptr[FILE], // socket fd comes from establishing a connection
    method: CString, // GET, POST, PUT, etc. but as a CString
    uri: CString // e.g. www.pragprog.com, but as a CString
): Unit = // \r\n is needed for carriage return, specified by HTTP
  stdio.fprintf(socketFileDescriptor, c"%s %s %s\r\n", method, uri, c"HTTP/1.1")

// write ONE header. This will be for-looped for all headers.
// Example header: Content-Type: text/html; charset=UTF-8
def writeHeader(socketFileDescriptor: Ptr[FILE], key: CString, value: CString): Unit =
  stdio.fprintf(socketFileDescriptor, c"%s: %s\r\n", key, value)

def writeBody(socketFileDescriptor: Ptr[FILE], body: CString): Unit =
  stdio.fputs(body, socketFileDescriptor) // put the whole thing, no need to format.

// write the request line, all the headers, and the request body.
def writeRequest(socketFileDescriptor: Ptr[FILE], request: HttpRequest): Unit =
  Zone: // implicit z => // 0.5
    writeRequestLine(
      socketFileDescriptor,
      toCString(request.method), // requires Zone
      toCString(request.uri) // requires Zone
    )

    for (key, value) <- request.headers
    do writeHeader(socketFileDescriptor, toCString(key), toCString(value)) // require Zone

    stdio.fputs(c"\n", socketFileDescriptor) // after headers, there is 1 empty line.
    writeBody(socketFileDescriptor, toCString(request.body)) // requires Zone

// Reading the response.
// Example status line: HTTP/1.1 200 OK. Return the status code (200).
def parseStatusLine(line: CString): Int =
  println("parsing status")
  val protocolPtr = stackalloc[Byte](64) // e.g. HTTP/1.1
  val codePtr = stackalloc[Int](1) // e.g. 200
  val descPtr = stackalloc[Byte](128) // e.g. OK
  val scanResult = stdio.sscanf(line, c"%s %d %s\n", protocolPtr, codePtr, descPtr)
  if scanResult < 3 then throw Exception("bad status line") else !codePtr

// parse ONE header line. This will be while-looped later. Example headers:
// key:           value          (should not be longer than 64 chars)
// Cache-Control: max-age=604800
def parseHeaderLine(line: CString): (String, String) =
  val keyBuffer = stackalloc[Byte](64)
  val valueBuffer = stackalloc[Byte](64)
  stdio.printf(c"about to sscanf line: '%s'\n", line)

  val scanResult = stdio.sscanf(line, c"%s %s\n", keyBuffer, valueBuffer)
  if scanResult < 2 then throw Exception("bad header line")
  else (fromCString(keyBuffer), fromCString(valueBuffer)) // Scala strings

def readResponse(socketFileDescriptor: Ptr[FILE]): HttpResponse =
  val lineBuffer = stdlib.malloc(4096) // big enough, will be reused // 0.5
  println("reading status line?")

  // read status line. fgets reads until newline.
  var readResult = stdio.fgets(lineBuffer, 4096, socketFileDescriptor)
  val code = parseStatusLine(lineBuffer) // 200

  var headers = MMap[String, String]()
  println("reading first response header")
  readResult = stdio.fgets(lineBuffer, 4096, socketFileDescriptor) // reuse lineBuffer
  var lineLength = string.strlen(lineBuffer)

  while lineLength.toInt > 2 do // "\n\n" has length 2, so keep reading until empty line.
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
    else 65536 - 1

  val bodyBuffer = stdlib.malloc(contentLength + 1) // 0.5
  val bodyReadResult =
    stdio.fread(
      bodyBuffer,
      1.toUSize, // 0.5
      contentLength.toUSize,
      socketFileDescriptor
    )

  val bodyLength = string.strlen(bodyBuffer)
  if bodyLength.toULong != contentLength.toULong then
    println(s"Warning: saw ${bodyLength} bytes, but expected ${contentLength}")

  HttpResponse(code, headers, fromCString(bodyBuffer))

def makeConnection(address: CString, port: CString): Int =
  val hints = stackalloc[addrinfo](1)
  string.memset(hints.asInstanceOf[Ptr[Byte]], 0, sizeof[addrinfo])
  hints.ai_family = AF_UNSPEC
  hints.ai_socktype = SOCK_STREAM

  val addrInfoPtr: Ptr[Ptr[addrinfo]] = stackalloc[Ptr[addrinfo]](1)
  println("about to perform lookup")

  val lookupResult = getaddrinfo(address, port, hints, addrInfoPtr)
  println(s"lookup returned ${lookupResult}")

  if lookupResult != 0 then
    val errString = util.gai_strerror(lookupResult)
    stdio.printf(c"errno: %d %s\n", lookupResult, errString)
    throw Exception("no address found")
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
    val sock = socket(addrInfo.ai_family, addrInfo.ai_socktype, addrInfo.ai_protocol)
    println(s"socket returned fd $sock")
    if sock < 0 then throw Exception("error in creating socket")

    println("connecting")
    val connectResult = connect(sock, addrInfo.ai_addr, addrInfo.ai_addrlen)
    println(s"connect returned $connectResult")

    if connectResult != 0 then
      val err = errno.errno
      val errString = string.strerror(err)
      stdio.printf(c"errno: %d %s\n", err, errString)
      throw Exception("connection failed")

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
@main
def httpClient(args: String*): Unit =
  if args.length != 3 then
    println(s"${args.length} {args}")
    println("Usage: ./tcp_test [address] [port] [path]")

  Zone: // implicit z => // 0.5
    val (address, host) = (toCString(args(0)), args(0))
    val (port, path) = (toCString(args(1)), args(2))
    stdio.printf(c"looking up address: %s port: %s\n", address, port)

    val sock = makeConnection(address, port)
    handleConnection(sock, host, path)

@extern
object util:
  def getaddrinfo(
      address: CString, // hostname to lookup, e.g. "www.parprog.com"
      port: CString, // port number, e.g. "80"
      hints: Ptr[addrinfo], // partially populated addrinfo struct object
      res: Ptr[Ptr[addrinfo]] // to hold the result
  ): Int = extern
  def socket(family: Int, socktype: Int, protocol: Int): Int = extern
  def connect(sock: Int, addrInfo: Ptr[sockaddr], addrLen: CInt): Int = extern
  def gai_strerror(code: Int): CString = extern
  def fdopen(fd: Int, mode: CString): Ptr[FILE] = extern
