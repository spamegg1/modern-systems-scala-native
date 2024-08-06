package ch07
package curlSync

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.*
import scalanative.libc.stdlib
import scalanative.libc.stdio.{fwrite, stdout}
import scalanative.libc.string
import LibCurl.*, LibCurlConstants.*
import scala.collection.mutable.HashMap

object CurlBasic:
  def addHeaders(curl: Curl, headers: Seq[String]): Ptr[CurlSList] =
    var slist: Ptr[CurlSList] = null
    for h <- headers do addHeader(slist, h)
    curl_easy_setopt(curl, HTTPHEADER, slist.asInstanceOf[Ptr[Byte]])
    slist

  def addHeader(slist: Ptr[CurlSList], header: String): Ptr[CurlSList] =
    Zone(slist_append(slist, toCString(header))) // 0.5

  var requestSerial = 0L
  val responses = HashMap[Long, ResponseState]()

  def getSync(url: String, headers: Seq[String] = Seq.empty): ResponseState =
    val reqIdPtr = stdlib.malloc(sizeof[Long]).asInstanceOf[Ptr[Long]]
    !reqIdPtr = 1 + requestSerial
    requestSerial += 1
    responses(requestSerial) = ResponseState()
    val curl = easy_init()

    Zone:
      val url_str = toCString(url)
      println(curl_easy_setopt(curl, URL, url_str))

    curl_easy_setopt(curl, WRITECALLBACK, Curl.funcToPtr(writeCB))
    curl_easy_setopt(curl, WRITEDATA, reqIdPtr.asInstanceOf[Ptr[Byte]])
    curl_easy_setopt(curl, HEADERCALLBACK, Curl.funcToPtr(headerCB))
    curl_easy_setopt(curl, HEADERDATA, reqIdPtr.asInstanceOf[Ptr[Byte]])

    val res = easy_perform(curl)
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
      val serial = !(data.asInstanceOf[Ptr[Long]])
      val len = stackalloc[Double]()
      !len = 0
      val strData = bufferToString(ptr, size, nmemb)

      val resp = responses(serial)
      resp.body = resp.body + strData
      responses(serial) = resp

      size * nmemb

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
      val serial = !(data.asInstanceOf[Ptr[Long]])
      val len = stackalloc[Double](1)
      !len = 0
      val byteSize = size * nmemb
      val headerString = bufferToString(ptr, size, nmemb)

      headerString match
        case statusLine(code, description) => println(s"status code: $code $description")
        case headerLine(k, v) =>
          val resp = responses(serial)
          resp.headers(k) = v
          responses(serial) = resp
        case l =>

      fwrite(ptr, size, nmemb, stdout)
      byteSize

  def main(args: String*): Unit =
    println("initializing")
    global_init(1)
    val response = getSync(args(0))
    println(s"done.  got response: $response")
    println("global cleanup...")
    global_cleanup()
    println("done")
