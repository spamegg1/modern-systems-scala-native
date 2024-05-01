package ch06
package asyncHttp

import scalanative.unsigned.{UnsignedRichInt, UShort}
import scalanative.unsafe.{CString, CSize, CQuote, fromCString, Ptr, stackalloc, CStruct2}
import scalanative.unsafe.{Zone, toCString}
import scalanative.libc.stdlib.malloc
import scalanative.libc.stdio
import scalanative.libc.string.{strlen, strncpy}

object HTTP:
  import ch03.httpClient.{HttpRequest, HttpResponse}

  case class HeaderLine(
      key: CString,
      value: CString,
      keyLen: UShort,
      valueLen: UShort
  )
  type RequestHandler = Function1[HttpRequest, HttpResponse]
  type Buffer = CStruct2[Ptr[Byte], CSize]

  val HEADER_COMPLETE_NO_BODY = 0
  val HEADERS_INCOMPLETE = -1

  val MAX_URI_SIZE = 2048
  val MAX_METHOD_SIZE = 8

  val methodBuffer = malloc(16) // 0.5
  val uriBuffer = malloc(4096) // 0.5

  def scanRequestLine(line: CString): (String, String, Int) =
    val lineLen = stackalloc[Int](1)
    val scanResult =
      stdio.sscanf(line, c"%s %s %*s\r\n%n", methodBuffer, uriBuffer, lineLen)
    if scanResult == 2 then (fromCString(methodBuffer), fromCString(uriBuffer), !lineLen)
    else throw Exception("bad request line")

  def scanHeaderLine(
      line: CString,
      outMap: collection.mutable.Map[String, String],
      keyEnd: Ptr[Int],
      valueStart: Ptr[Int],
      valueEnd: Ptr[Int],
      lineLen: Ptr[Int]
  ): Int =
    !lineLen = -1
    val scanResult = stdio.sscanf(
      line,
      c"%*[^\r\n:]%n: %n%*[^\r\n]%n%*[\r\n]%n",
      keyEnd,
      valueStart,
      valueEnd,
      lineLen
    )
    if !lineLen != -1 then
      val startOfKey = line
      val endOfKey = line + !keyEnd
      !endOfKey = 0

      val startOfValue = line + !valueStart
      val endOfValue = line + !valueEnd
      !endOfValue = 0

      val key = fromCString(startOfKey)
      val value = fromCString(startOfValue)
      outMap(key) = value

      !lineLen
    else throw Exception("bad header line")

  val lineBuffer = malloc(1024) // 0.5

  def parseRequest(req: CString, size: Long): HttpRequest =
    req(size) = 0.toByte // ensure null termination
    var reqPosition = req
    val lineLen = stackalloc[Int](1)
    val keyEnd = stackalloc[Int](1)
    val valueStart = stackalloc[Int](1)
    val valueEnd = stackalloc[Int](1)
    val headers = collection.mutable.Map[String, String]()

    val (method, uri, requestLen) = scanRequestLine(req)
    var bytesRead = requestLen

    while bytesRead < size do
      reqPosition = req + bytesRead
      val parseHeaderResult =
        scanHeaderLine(reqPosition, headers, keyEnd, valueStart, valueEnd, lineLen)
      if parseHeaderResult < 0 then throw Exception("HEADERS INCOMPLETE")
      else if !lineLen - !valueEnd == 2 then bytesRead += parseHeaderResult
      else if !lineLen - !valueEnd == 4 then
        val remaining = size - bytesRead
        val body = fromCString(req + bytesRead)
        HttpRequest(method, uri, headers, body)
      else throw Exception("malformed header!")

    throw Exception(s"bad scan, exceeded $size bytes")

  val keyBuffer = malloc(512) // 0.5
  val valueBuffer = malloc(512) // 0.5
  val bodyBuffer = malloc(4096) // 0.5

  def makeResponse(response: HttpResponse, buffer: Ptr[Buffer]): Unit =
    stdio.snprintf(buffer._1, 4096.toUSize, c"HTTP/1.1 200 OK\r\n") // 0.5
    var headerPos = 0
    val bufferStart = buffer._1
    var bytesWritten = strlen(bufferStart)
    var lastPosition = bufferStart + bytesWritten
    var bytesRemaining = 4096.toUSize - bytesWritten // 0.5
    val headers = response.headers.keys.toSeq

    while headerPos < response.headers.size do
      val k = headers(headerPos)
      val v = response.headers(k)
      Zone:
        val keyTemp = toCString(k)
        val valueTemp = toCString(v)
        strncpy(keyBuffer, keyTemp, 512.toUSize) // 0.5
        strncpy(valueBuffer, valueTemp, 512.toUSize) // 0.5

      stdio.snprintf(lastPosition, bytesRemaining, c"%s: %s\r\n", keyBuffer, valueBuffer)

      val len = strlen(lastPosition)
      bytesWritten = bytesWritten + len + 1.toUSize // 0.5
      bytesRemaining = 4096.toUSize - bytesWritten // 0.5
      lastPosition = lastPosition + len
      headerPos += 1

    Zone:
      val body = toCString(response.body)
      val bodyLen = strlen(body)
      strncpy(bodyBuffer, body, 4096.toUSize) // 0.5

    stdio.snprintf(lastPosition, bytesRemaining, c"\r\n%s", bodyBuffer)
