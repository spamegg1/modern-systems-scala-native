package ch05

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.{CString, CQuote, stackalloc, fromCString, toCString}
import scalanative.unsafe.{Zone, extern, Ptr}
import scalanative.libc.{stdio, stdlib, string, errno}, stdio.FILE

import ch03.httpClient.{HttpRequest, HttpResponse}

object Parsing:
  // Example header: Content-Type: text/html; charset=UTF-8
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

    val scanResult = // GET /index.html HTTP/1.1
      stdio.sscanf(line, c"%s %s %s\n", methodBuffer, urlBuffer, protocolBuffer)
    if scanResult < 3 then throw Exception("bad request line")
    else (fromCString(methodBuffer), fromCString(urlBuffer))

  def parseRequest(conn: Int): Option[HttpRequest] =
    val socketFd = util.fdopen(conn, c"r")
    val lineBuffer = stdlib.malloc(4096) // fix // 0.5
    var readResult = stdio.fgets(lineBuffer, 4096, socketFd)

    val (method, url) = parseRequestLine(lineBuffer)
    stdio.printf(c"read request line: %s", lineBuffer)
    println(s"${(method, url)}")

    val headers = collection.mutable.Map[String, String]()
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
    Zone: // implicit z => // 0.5
      stdio.fprintf(socketFd, c"%s %s %s\r\n", c"HTTP/1.1", c"200", c"OK")

      for (k, v) <- resp.headers do
        stdio.fprintf(socketFd, c"%s: %s\r\n", toCString(k), toCString(v))

      stdio.fprintf(socketFd, c"\r\n")
      stdio.fprintf(socketFd, toCString(resp.body))

    stdio.fclose(socketFd)

@extern
object util:
  def fdopen(fd: Int, mode: CString): Ptr[FILE] = extern
