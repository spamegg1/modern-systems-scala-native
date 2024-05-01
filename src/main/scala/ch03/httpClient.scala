package ch03
package httpClient

import scalanative.unsafe.{Zone, Ptr, CInt, CString, toCString, fromCString, CQuote}
import scalanative.unsafe.{stackalloc, extern}
import scalanative.unsigned.{USize, UnsignedRichInt}
import scalanative.libc.{stdio, stdlib, string}
import stdio.{FILE, fclose}

import collection.mutable.{Map => MMap}

// These are Scala strings, they will be converted to C-strings later.
case class HttpRequest(
    method: String, // GET, POST, PUT, etc.
    uri: String, // e.g. www.pragprog.com
    headers: collection.Map[String, String], // supertype of both mutable and immutable
    body: String
)
case class HttpResponse(
    code: Int, // 200, 301, 404, 500, etc.
    headers: collection.Map[String, String],
    body: String
)

// Example HTTP request line: GET /index.html HTTP/1.1
def writeRequestLine(
    socketFileDesc: Ptr[FILE], // socket fd comes from establishing a connection
    method: CString, // GET, POST, PUT, etc. but as a CString
    uri: CString // e.g. www.pragprog.com, but as a CString
): Unit = // \r\n is needed for carriage return, specified by HTTP
  stdio.fprintf(socketFileDesc, c"%s %s %s\r\n", method, uri, c"HTTP/1.1")

// write ONE header. This will be for-looped for all headers.
// Example header: Content-Type: text/html; charset=UTF-8
def writeHeader(socketFileDesc: Ptr[FILE], key: CString, value: CString): Unit =
  stdio.fprintf(socketFileDesc, c"%s: %s\r\n", key, value)

def writeBody(socketFileDesc: Ptr[FILE], body: CString): Unit =
  stdio.fputs(body, socketFileDesc) // put the whole thing, no need to format.

// write the request line, all the headers, and the request body.
def writeRequest(socketFileDesc: Ptr[FILE], request: HttpRequest): Unit = Zone: // 0.5
  writeRequestLine(socketFileDesc, toCString(request.method), toCString(request.uri))

  for (key, value) <- request.headers // write all headers
  do writeHeader(socketFileDesc, toCString(key), toCString(value)) // require Zone

  stdio.fputs(c"\n", socketFileDesc) // after headers, there is 1 empty line.
  writeBody(socketFileDesc, toCString(request.body)) // toCString requires Zone

// Reading the response.
// Example status line: HTTP/1.1 200 OK. Return the status code (200).
def parseStatusLine(line: CString): Int =
  println("parsing status") // there should be 3 items.
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

def readResponse(socketFileDesc: Ptr[FILE]): HttpResponse =
  val lineBuffer = stdlib.malloc(4096) // big enough, will be reused // 0.5
  println("reading status line?")

  // read status line. fgets reads until newline.
  var readResult = stdio.fgets(lineBuffer, 4096, socketFileDesc)
  val code = parseStatusLine(lineBuffer) // e.g. 200

  var headers = MMap[String, String]() // e.g. "Cache-Control" -> "max-value=604800"

  // This is a do-while pattern. The first one is read separately from loop.
  println("reading first response header")
  readResult = stdio.fgets(lineBuffer, 4096, socketFileDesc) // reuse lineBuffer
  var lineLength = string.strlen(lineBuffer) // to check for empty line.

  while lineLength.toInt > 2 do // keep reading headers until empty line.
    val (k, v) = parseHeaderLine(lineBuffer)
    println(s"${(k, v)}") // the header
    headers(k) = v // add it to our map
    println("reading header")
    readResult = stdio.fgets(lineBuffer, 4096, socketFileDesc) // reuse lineBuffer
    lineLength = string.strlen(lineBuffer) // update to see if it's empty line.

  val contentLength: Int =
    if headers.contains("Content-Length:") then
      println("saw content-length")
      headers("Content-Length:").toInt
    else 65536 - 1 // room for null terminator

  // content length determines size of body.
  val bodyBuffer = stdlib.malloc(contentLength + 1) // add null terminator // 0.5
  val bodyReadResult =
    stdio.fread(bodyBuffer, 1.toUSize, contentLength.toUSize, socketFileDesc) // 0.5

  val bodyLength: USize = string.strlen(bodyBuffer)
  if bodyLength != contentLength then // 0.5, we can now compare USize/CSize with Int!
    println(s"Warning: saw ${bodyLength} bytes, but expected ${contentLength}")

  HttpResponse(code, headers, fromCString(bodyBuffer)) // all done!

//  e.g.             1234       www.example.com     /
def handleConnection(sock: Int, host: String, path: String): Unit =
  val socketFileDesc: Ptr[FILE] = util.fdopen(sock, c"r+") // convert socket to FILE
  val headers = Map("Host" -> host)
  val request = HttpRequest("GET", path, headers, "") // request has empty body

  writeRequest(socketFileDesc, request) // write http request data on socketFd
  println("wrote request")
  stdio.fflush(socketFileDesc) // fflush writes all the data to the output stream.

  val response = readResponse(socketFileDesc) // response comes from same socketFd
  println(s"got Response: ${response}")
  fclose(socketFileDesc) // don't forget to close file descriptor!

@main
def run(args: String*): Unit =
  if args.length != 3 then // args = www.example.com 80 /
    println(s"${args.length} {args}")
    println("Usage: ./tcp_test [address] [port] [path]")

  Zone: // implicit z => // 0.5
    val (address, host) = (toCString(args(0)), args(0)) // requires Zone
    val (port, path) = (toCString(args(1)), args(2)) // requires Zone
    stdio.printf(c"looking up address: %s port: %s\n", address, port) // no println (Zone)

    val sock = makeConnection(address, port) // establish connection
    handleConnection(sock, host, path) // do request / response on connection
