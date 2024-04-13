package ch09
package lmdbWeb

import scalanative.unsigned.{UnsignedRichLong, UnsignedRichInt}
import scalanative.unsafe.*
import scalanative.unsigned.*
import scalanative.libc.*
import stdio.*
import stdlib.*
import string.*
import collection.mutable

case class HeaderLine(
    key: CString,
    value: CString,
    keyLen: UShort,
    valueLen: UShort
)
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

object HTTP:
  import LibUV.*, ch07.LibUVConstants.*

  type RequestHandler = Function1[HttpRequest, HttpResponse]

  val HEADER_COMPLETE_NO_BODY = 0
  val HEADERS_INCOMPLETE = -1

  val MAX_URI_SIZE = 2048
  val MAX_METHOD_SIZE = 8

  val methodBuffer = malloc(16) // 0.5
  val uriBuffer = malloc(4096) // 0.5

  def scanRequestLine(line: CString): (String, String, Int) =
    val lineLen = stackalloc[Int](1)
    val scanResult = stdio.sscanf(
      line,
      c"%s %s %*s\r\n%n",
      methodBuffer,
      uriBuffer,
      lineLen
    )
    if scanResult == 2 then (fromCString(methodBuffer), fromCString(uriBuffer), !lineLen)
    else throw Exception("bad request line")

  def scanHeaderLine(
      line: CString,
      outMap: mutable.Map[String, String],
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
    // val lineBuffer = stackalloc[CChar](1024)
    val lineLen = stackalloc[Int](1)
    val keyEnd = stackalloc[Int](1)
    val valueStart = stackalloc[Int](1)
    val valueEnd = stackalloc[Int](1)
    val headers = mutable.Map[String, String]()

    val (method, uri, requestLen) = scanRequestLine(req)

    var bytesRead = requestLen
    while bytesRead < size do
      reqPosition = req + bytesRead
      val parseHeaderResult = scanHeaderLine(
        reqPosition,
        headers,
        keyEnd,
        valueStart,
        valueEnd,
        lineLen
      )
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
    var lastPos = bufferStart + bytesWritten
    var bytesRemaining = 4096.toUSize - bytesWritten // 0.5
    val headers = response.headers.keys.toSeq

    while headerPos < response.headers.size do
      val k = headers(headerPos)
      val v = response.headers(k)
      Zone: // implicit z => // 0.5
        val keyTemp = toCString(k)
        val valueTemp = toCString(v)
        strncpy(keyBuffer, keyTemp, 512.toUSize) // 0.5
        strncpy(valueBuffer, valueTemp, 512.toUSize) // 0.5

      stdio.snprintf(
        lastPos,
        bytesRemaining,
        c"%s: %s\r\n",
        keyBuffer,
        valueBuffer
      )
      val len = strlen(lastPos)
      bytesWritten = bytesWritten + len + 1.toUSize // 0.5
      bytesRemaining = 4096.toUSize - bytesWritten // 0.5
      lastPos = lastPos + len
      headerPos += 1

    Zone: // implicit z => // 0.5
      val body = toCString(response.body)
      val bodyLen = strlen(body)
      strncpy(bodyBuffer, body, bodyLen) // 0.5 // was 4096.toUSize

    stdio.snprintf(lastPos, bytesRemaining, c"\r\n%s", bodyBuffer)
