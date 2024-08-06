package ch07
package curlSync

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.*
import scalanative.libc.{stdlib, string, stdio}
import LibCurl.*, LibCurlConstants.*
import scala.collection.mutable.HashMap

object CurlBasic:
  def addHeaders(curl: Curl, headers: Seq[String]): Ptr[CurlSList] =
    var slist: Ptr[CurlSList] = null
    for h <- headers do addHeader(slist, h)
    curl_easy_setopt(curl, HTTPHEADER, slist.asInstanceOf[Ptr[Byte]])
    slist

  def addHeader(slist: Ptr[CurlSList], header: String): Ptr[CurlSList] = Zone:
    slist_append(slist, toCString(header))

  var requestSerial = 0L
  val responses = HashMap[Long, ResponseState]()

  // initialize a request and set up all of its options, headers, and callbacks
  def getSync(url: String, headers: Seq[String] = Seq.empty): ResponseState =
    val reqIdPtr = stdlib.malloc(sizeof[Long]).asInstanceOf[Ptr[Long]]
    requestSerial += 1
    !reqIdPtr = requestSerial // unique serial number for request
    responses(requestSerial) = ResponseState() // 200, no headers, empty body
    val curl = easy_init()

    Zone:
      val urlStr = toCString(url)
      println(curl_easy_setopt(curl, URL, urlStr))

    curl_easy_setopt(curl, WRITECALLBACK, Curl.funcToPtr(writeCB))
    curl_easy_setopt(curl, WRITEDATA, reqIdPtr.asInstanceOf[Ptr[Byte]])
    curl_easy_setopt(curl, HEADERCALLBACK, Curl.funcToPtr(headerCB))
    curl_easy_setopt(curl, HEADERDATA, reqIdPtr.asInstanceOf[Ptr[Byte]])

    val _ = easy_perform(curl)
    easy_cleanup(curl)
    responses(requestSerial)

  def bufferToString(ptr: Ptr[Byte], size: CSize, nmemb: CSize): String =
    val byteSize = size * nmemb
    val buffer = stdlib.malloc(byteSize) // removed the +1 s here
    string.strncpy(buffer, ptr, byteSize) // removed the +1 s here
    val res = fromCString(buffer)
    stdlib.free(buffer)
    res

  val writeCB = CFuncPtr4.fromScalaFunction[Ptr[Byte], CSize, CSize, Ptr[Byte], CSize]:
    (ptr: Ptr[Byte], size: CSize, nmemb: CSize, data: Ptr[Byte]) =>
      val serial: Long = !(data.asInstanceOf[Ptr[Long]])
      val len: Ptr[Double] = stackalloc[Double](1)
      !len = 0
      val strData: String = bufferToString(ptr, size, nmemb)

      val resp = responses(serial)
      resp.body += strData
      responses(serial) = resp

      size * nmemb // return size of data written

  // .+?   : match any chars one or more times, but shortest match possible until space.
  // (\d+) : match any digits one or more times. (capture group)
  // (.+)  : match any chars one or more times, as long as possible, until space. (group)
  // For example:    .+?   (\d+) (.+)
  //              HTTP/1.1  200   OK
  val statusLine = raw".+? (\d+) (.+)\n".r

  // ([^:]+) : match any chars except : one or more times. (capture group)
  // (.*)    : match any chars 0 or more times, as long as possible, until space. (group)
  // For example, ([^:]+): (   .*    )
  //                 Host: 192.168.1.1
  //                 Port: 8080
  val headerLine = raw"([^:]+): (.*)\n".r

  val headerCB = CFuncPtr4.fromScalaFunction[Ptr[Byte], CSize, CSize, Ptr[Byte], CSize]:
    (ptr: Ptr[Byte], size: CSize, nmemb: CSize, data: Ptr[Byte]) =>
      val serial: Long = !(data.asInstanceOf[Ptr[Long]])
      val len: Ptr[Double] = stackalloc[Double](1)
      !len = 0
      val byteSize = size * nmemb
      val headerString = bufferToString(ptr, size, nmemb)

      headerString match // 200 OK
        case statusLine(code, description) => println(s"status code: $code $description")
        case headerLine(k, v) => // Port: 8080
          val resp = responses(serial)
          resp.headers(k) = v // Port -> 8080
          responses(serial) = resp
        case _ => // do nothing

      stdio.fwrite(ptr, size, nmemb, stdio.stdout)
      byteSize
