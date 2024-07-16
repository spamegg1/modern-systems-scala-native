// This code is not meant to compile and run, it's conceptual pseudocode.
// I just made up some stuff to make it compile.

package ch07
package examples

import scalanative.unsigned.UnsignedRichLong
import scalanative.unsafe.*
import scalanative.libc.*
import string.*, stdlib.*, stdio.*
import concurrent.{ExecutionContext, ExecutionContextExecutor, Future, Promise}
import util.{Try, Success, Failure}
import collection.mutable
// import slogging.* // only available for Scala 2.13, Scala Native 0.3

final case class RequestData(
    promise: Promise[String],
    var allocated: Long,
    var used: Long,
    var data: Ptr[Byte]
)

trait LoopExtension // made it up!
trait LazyLogging // made it up!

object CurlExample extends LazyLogging with LoopExtension:
  import LibCurl.*, LibCurlConstants.*

  var activeRequests = 0
  var serial = 0L
  val requests = mutable.HashMap[Long, RequestData]()
  val loop = EventLoop.loop
  var multi: MultiCurl = null
  var handle: IdleHandle = null

  def init: Unit =
    if multi == null then
      global_init(1)
      multi = multi_init()
      handle = initDispatcher(loop, multi)
      // EventLoop.addExtension(this) // had to leave this out.

  def dataReady(ptr: Ptr[Byte], size: CSize, nmemb: CSize, data: Ptr[Long]): CSize =
    stdio.printf(c"got back data ptr %p with private data %p %d\n", ptr, data, !data)
    val id = !data
    val request = requests(id)
    val readSize: CSize = size * nmemb
    val newSize = request.used + readSize.toLong // not sure if this is OK!
    if newSize >= request.allocated then
      val blocks = (newSize / 65536) + 1
      val newAllocatedSize = blocks * 65536
      val newMem = realloc(request.data, newAllocatedSize)
      request.data = newMem
      request.allocated = newAllocatedSize

    memcpy(request.data + request.used, ptr, readSize)
    printf(c"req %d: got body line of size %d\n", !request.data, size) // ???
    request.used = newSize
    val stringEnd = request.data + newSize
    !stringEnd = 0

    // We don't have slogging... so I commented out these.
    // logger.debug(s"""got data of size ${size} x ${nmemb} (req id: ${id},
    //   sed_size: ${request.used}, allocated_size: ${request.allocated})""")
    // logger.trace(c"privateData address: %p, buffer address: %p\n", d, !d._1)

    size * nmemb

  val writeCB = CFuncPtr4.fromScalaFunction(dataReady) // changed this! 0.5

  def writeHeader(
      ptr: Ptr[Byte],
      size: CSize,
      nmemb: CSize,
      data: Ptr[CurlBuffer]
  ): CSize =
    // Refactor!
    val len = stackalloc[Double](1)
    !len = 0
    println(
      easy_getinfo(
        data.asInstanceOf[Ptr[Byte]],
        CONTENTLENGTHDOWNLOADT,
        len.asInstanceOf[Ptr[Byte]]
      )
    )
    val byteSize = size * nmemb
    stdio.printf(c"got header line of size %d, body length %f: ", !len, byteSize)
    fwrite(ptr, size, nmemb, stdout)
    byteSize

  def makeRequest(
      method: Int,
      url: String,
      headers: Seq[(String, String)] = Seq(),
      body: Option[String] = None
  ): CurlRequest =
    val data = RequestData(Promise[String](), 65536, 0, malloc(65536))
    val curlHandle = easy_init()
    Zone:
      curl_easy_setopt(curlHandle, URL, toCString(url))
      // curl_easy_setopt(curlHandle, WRITECALLBACK, writeCB) // !!!
      // curl_easy_setopt(curlHandle, WRITEDATA, idCell) // what's idCell? no idea.
      // curl_easy_setopt(curlHandle, PRIVATEDATA, idCell)
    ???

  def simpleRequest( // made it up!
      method: CurlOption,
      url: String,
      headers: Seq[(String, String)],
      body: Option[String]
  ) = ???

  def simpleGet(url: String, headers: Seq[(String, String)]): CurlRequest =
    simpleRequest(GET, url, headers, None)

  def simplePost(
      url: String,
      headers: Seq[(String, String)],
      body: Option[String]
  ): CurlRequest =
    simpleRequest(PUT, url, headers, body)

  def makeRequestAsync(
      method: Int,
      url: String,
      headers: Seq[(String, String)] = Seq(),
      body: Option[String] = None
  ): Future[String] =
    val id = serial
    val idCell = malloc(sizeof[Long]).asInstanceOf[Ptr[Long]]
    !idCell = id
    printf(c"created malloc cell at %p to hold serial # %d\n", idCell, id)
    serial += 1
    val data = RequestData(Promise[String](), 65536, 0, malloc(65536))
    activeRequests += 1
    requests(id) = data

    val curlHandle = easy_init()
    Zone:
      curl_easy_setopt(curlHandle, URL, toCString(url))
      // curl_easy_setopt(curlHandle, WRITECALLBACK, writeCB) // can't figure out
      // curl_easy_setopt(curlHandle, WRITEDATA, idCell)
      // curl_easy_setopt(curlHandle, PRIVATEDATA, idCell)
    multi_add_handle(multi, curlHandle)
    data.promise.future

  def completeRequest(serial: Long): Unit =
    val req = requests.remove(serial).get
    activeRequests -= 1
    val result = fromCString(req.data)
    free(req.data)
    req.promise.success(result)

  def checkCurl(handle: IdleHandle): Unit =
    println("checking curl status")
    val multi = !(handle.asInstanceOf[Ptr[MultiCurl]])
    val requests = stackalloc[Int](1)
    val perform_result = multi_perform(multi, requests)
    activeRequests = !requests

    val messages_left = stackalloc[Int](1)
    var message = multi_info_read(multi, messages_left)
    while message != null do
      val easy = message._2 // was !message._2
      val requestDataPtr = stackalloc[Ptr[Long]](1) // ??? this was []
      easy_getinfo(easy, GET_PRIVATEDATA, requestDataPtr.asInstanceOf[Ptr[Byte]])
      val requestIdPtr: Ptr[Long] = !requestDataPtr
      val requestId: Long = !requestIdPtr
      printf(
        c"got message: %d, %p (%p : %d)\n",
        message._1, // was !message._1
        requestDataPtr,
        requestIdPtr,
        requestId
      )
      completeRequest(requestId)
      free(requestIdPtr.asInstanceOf[Ptr[Byte]])
      message = multi_info_read(multi, messages_left)

  val check_cb = CFuncPtr1.fromScalaFunction(checkCurl)

  private def initDispatcher(loop: LibUV.Loop, multi: MultiCurl): IdleHandle =
    println("initializing curl dispatcher")

    // can't find these UV functions and constants anywhere.
    // val handle = stdlib.malloc(uv_handle_size(UV_IDLE_T))
    // checkError(uv_idle_init(loop, handle), "uv_idle_init_curl")
    !(handle.asInstanceOf[Ptr[MultiCurl]]) = multi
    // checkError(uv_idle_start(handle, check_cb), "uv_idle_start_curl")
    handle

  // def close(): Unit = uv_idle_stop(handle)
