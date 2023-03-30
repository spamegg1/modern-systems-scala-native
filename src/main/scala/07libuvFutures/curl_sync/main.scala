package `07curlSync`

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.*
import scalanative.libc.*
import LibCurl.*, LibCurlConstants.*
import stdlib.*
import stdio.*
import string.*
import scala.collection.mutable.HashMap

object curlBasic:
  def addHeaders(curl: Curl, headers: Seq[String]): Ptr[CurlSList] =
    var slist: Ptr[CurlSList] = null
    for h <- headers do addHeader(slist, h)
    curl_easy_setopt(curl, HTTPHEADER, slist.asInstanceOf[Ptr[Byte]])
    slist

  def addHeader(slist: Ptr[CurlSList], header: String): Ptr[CurlSList] =
    Zone(implicit z => slist_append(slist, toCString(header)))

  var request_serial = 0L
  val responses = HashMap[Long, ResponseState]()

  def getSync(url: String, headers: Seq[String] = Seq.empty): ResponseState =
    val req_id_ptr = malloc(sizeof[Long]).asInstanceOf[Ptr[Long]]
    !req_id_ptr = 1 + request_serial
    request_serial += 1
    responses(request_serial) = ResponseState()
    val curl = easy_init()

    Zone(implicit z =>
      val url_str = toCString(url)
      println(curl_easy_setopt(curl, URL, url_str))
    )
    curl_easy_setopt(curl, WRITECALLBACK, Curl.func_to_ptr(writeCB))
    curl_easy_setopt(curl, WRITEDATA, req_id_ptr.asInstanceOf[Ptr[Byte]])
    curl_easy_setopt(curl, HEADERCALLBACK, Curl.func_to_ptr(headerCB))
    curl_easy_setopt(curl, HEADERDATA, req_id_ptr.asInstanceOf[Ptr[Byte]])
    val res = easy_perform(curl)
    easy_cleanup(curl)
    responses(request_serial)

  def bufferToString(ptr: Ptr[Byte], size: CSize, nmemb: CSize): String =
    val byteSize = size * nmemb
    val buffer = malloc(byteSize) // removed the +1 s here
    strncpy(buffer, ptr, byteSize) // removed the +1 s here
    val res = fromCString(buffer)
    free(buffer)
    (res)

  // val writeCB = new CurlDataCallback:
  val writeCB =
    CFuncPtr4.fromScalaFunction[Ptr[Byte], CSize, CSize, Ptr[Byte], CSize](
      (
          ptr: Ptr[Byte],
          size: CSize,
          nmemb: CSize,
          data: Ptr[Byte]
      ) =>
        val serial = !(data.asInstanceOf[Ptr[Long]])
        val len = stackalloc[Double](sizeof[Double])
        !len = 0
        val strData = bufferToString(ptr, size, nmemb)

        val resp = responses(serial)
        resp.body = resp.body + strData
        responses(serial) = resp

        size * nmemb
    )

  val statusLine = raw".+? (\d+) (.+)\n".r
  val headerLine = raw"([^:]+): (.*)\n".r
  // val headerCB = new CurlDataCallback:
  val headerCB =
    CFuncPtr4.fromScalaFunction[Ptr[Byte], CSize, CSize, Ptr[Byte], CSize](
      (
          ptr: Ptr[Byte],
          size: CSize,
          nmemb: CSize,
          data: Ptr[Byte]
      ) =>
        val serial = !(data.asInstanceOf[Ptr[Long]])
        val len = stackalloc[Double](sizeof[Double])
        !len = 0
        val byteSize = size * nmemb
        val headerString = bufferToString(ptr, size, nmemb)

        headerString match
          case statusLine(code, description) =>
            println(s"status code: $code $description")
          case headerLine(k, v) =>
            val resp = responses(serial)
            resp.headers(k) = v
            responses(serial) = resp
          case l =>

        fwrite(ptr, size, nmemb, stdout)
        byteSize
    )

  def main(args: String*): Unit =
    println("initializing")
    global_init(1)
    val resp = getSync(args(0))
    println(s"done.  got response: $resp")
    println("global cleanup...")
    global_cleanup()
    println("done")
