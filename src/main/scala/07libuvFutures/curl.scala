/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
import scalanative.native._
import slogging._
import string._, stdlib._, stdio._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.{Try, Success, Failure}
import scala.collection.mutable
import LibUV._
import LibUVConstants._

final case class RequestData(promise:Promise[String], var allocated:Long, var used:Long, var data:Ptr[Byte])

object Curl extends LoopExtension with LazyLogging {
  import LibCurl._, CurlConstants._

  var activeRequests = 0
  var serial = 0L
  val requests = mutable.HashMap[Long,RequestData]()
  val loop = EventLoop.loop
  var multi:MultiCurl = null
  var handle:IdleHandle = null
  def init():Unit = if (multi == null) {
    global_init(1)
    multi = multi_init()
    handle = initDispatcher(loop,multi)
    EventLoop.addExtension(this)
    ()
  }

  def dataReady(ptr: Ptr[Byte], size: CSize, nmemb: CSize, data: Ptr[Long]): CSize = {
    printf(c"got back data ptr %p with private data %p %d\n",ptr, data, !data)
    val id = !data
    val request = requests(id)
    val read_size = size * nmemb
    val new_size = request.used + read_size
    if (new_size >= request.allocated) {
      val blocks = (new_size / 65536) + 1
      val new_allocated_size = blocks * 65536
      val new_mem = realloc(request.data, new_allocated_size)
      request.data = new_mem
      request.allocated = new_allocated_size
    }
    memcpy(request.data + request.used, ptr, read_size)    
    // printf(c"req %d: got body line of size %d\n", !private_data._4, size)
    request.used = new_size
    val stringEnd = request.data + new_size
    !stringEnd = 0

    logger.debug(s"got data of size ${size} x ${nmemb} (req id: ${id}, used_size: ${request.used}, allocated_size: ${request.allocated})")
    // logger.trace(c"privateData address: %p, buffer address: %p\n", d, !d._1)
    return size * nmemb
  }

  val write_cb = CFunctionPtr.fromFunction4(dataReady)

  def writeHeader(ptr: Ptr[Byte], size: CSize, nmemb: CSize, data: Ptr[CurlBuffer]): CSize = {
      // Refactor!
      val len = stackalloc[Double]
      !len = 0
      println(easy_getinfo(data.cast[Ptr[Byte]], CONTENTLENGTHDOWNLOADT, len))
      val byteSize = size * nmemb
      printf(c"got header line of size %d, body length %f: ", !len, byteSize)
      fwrite(ptr, size, nmemb, stdout)
      return byteSize
  }


  val makeRequest(method:Int, url:String, headers:Seq[(String,String)] = Seq(), body:Option[String] = None):CurlRequest = {
    val data = RequestData(Promise[String](), 65536, 0, malloc(65536))
    val curlHandle = easy_init()
    Zone { implicit z =>
      easy_setopt(curlHandle, URL, toCString(url))
    }
    easy_setopt(curlHandle, WRITECALLBACK, write_cb)
    easy_setopt(curlHandle, WRITEDATA, idCell)
    easy_setopt(curlHandle, PRIVATEDATA, idCell)
  }

  def simpleGet(url:String, headers:Seq[(String,String)]):CurlRequest = {
    simpleRequest(GET,url,headers,None)
  }

  def simplePost(url:String, headers:Seq[(String,String)], body:Option[String]):CurlRequest = {
    simpleRequest(PUT,url,headers,Some,body)
  }

  def makeRequestAsync(method:Int, url:String, headers:Seq[(String,String)] = Seq(), body:Option[String] = None):Future[String] = {
    val id = serial 
    val idCell = malloc(sizeof[Long]).cast[Ptr[Long]]
    !idCell = id
    printf(c"created malloc cell at %p to hold serial # %d\n", idCell, id)
    serial += 1
    val data = RequestData(Promise[String](), 65536, 0, malloc(65536))
    activeRequests += 1
    requests(id) = data

    val curlHandle = easy_init()
    Zone { implicit z =>
      easy_setopt(curlHandle, URL, toCString(url))
    }
    easy_setopt(curlHandle, WRITECALLBACK, write_cb)
    easy_setopt(curlHandle, WRITEDATA, idCell)
    easy_setopt(curlHandle, PRIVATEDATA, idCell)
    multi_add_handle(multi, curlHandle)
    data.promise.future
  }

  def completeRequest(serial:Long):Unit = {
    val req = requests.remove(serial).get
    activeRequests -= 1
    val result = fromCString(req.data)
    // free(req.data)
    req.promise.success(result)
  }

  def checkCurl(handle:IdleHandle):Unit = {
    // println("checking curl status")
    val multi = !(handle.cast[Ptr[MultiCurl]])
    val requests = stackalloc[Int]
    val perform_result = multi_perform(multi, requests)
    activeRequests = !requests

    val messages_left = stackalloc[Int]
    var message = multi_info_read(multi, messages_left)
    while (message != null) {
      val easy = !message._2
      val requestDataPtr = stackalloc[Ptr[Long]]
      easy_getinfo(easy, GET_PRIVATEDATA, requestDataPtr)
      val requestIdPtr = !requestDataPtr
      val requestId = !requestIdPtr
      printf(c"got message: %d, %p (%p : %d)\n", !message._1, requestDataPtr, requestIdPtr, requestId)
      completeRequest(requestId)
      free(requestIdPtr.cast[Ptr[Byte]])
      message = multi_info_read(multi, messages_left)
    }
  }

  val check_cb = CFunctionPtr.fromFunction1(checkCurl)

  private def initDispatcher(loop:LibUV.Loop, multi:MultiCurl):IdleHandle = {
    println("initializing curl dispatcher")
    val handle = stdlib.malloc(uv_handle_size(UV_IDLE_T))
    check(uv_idle_init(loop, handle),"uv_idle_init_curl")
    !(handle.cast[Ptr[MultiCurl]]) = multi
    check(uv_idle_start(handle, check_cb),"uv_idle_start_curl")
    return handle
  }

  def close():Unit = {
    uv_idle_stop(handle)
  }
}

@link("curl")
@extern object LibCurl {    
  type MultiCurl = Ptr[Byte]
  type CurlBuffer = CStruct2[CString, CSize]
  type CurlRequest = CStruct4[Ptr[Byte],Long,Long,Int]
  type CurlMessage = CStruct3[Int, Curl, Ptr[Byte]]  

  type Curl = Ptr[Byte]
  type CurlOption = CInt   
  type CurlInfo = CInt

  @name("curl_global_init")
  def global_init(flags:Long):Unit = extern

  @name("curl_easy_init")
  def easy_init():Curl = extern

  @name("curl_easy_setopt")
  def easy_setopt(handle: Curl, option: CurlOption, parameter: Any): CInt = extern

  @name("curl_easy_getinfo")
  def easy_getinfo(handle: Curl, info: CurlInfo, parameter: Any): CInt = extern

  @name("curl_easy_perform")
  def easy_perform(easy_handle: Curl): CInt = extern

  @name("curl_global_cleanup")
  def global_cleanup():Unit = extern
  
  @name("curl_easy_cleanup")
  def easy_cleanup(handle: Curl): Unit = extern

  @name("curl_multi_init")
  def multi_init():MultiCurl = extern

  @name("curl_multi_add_handle")
  def multi_add_handle(multi:MultiCurl, easy:Curl):Int = extern

  @name("curl_multi_setopt")
  def multi_setopt(multi:MultiCurl, option:CInt, parameter:Any):CInt = extern

  @name("curl_multi_cleanup")
  def multi_cleanup(multi:MultiCurl):Int = extern

  @name("curl_multi_assign")
  def multi_assign(multi:MultiCurl, socket:Ptr[Byte], socket_data:Ptr[Byte]):Int = extern

  @name("curl_multi_socket_action")
  def multi_socket_action(multi:MultiCurl, socket:Ptr[Byte], events:Int, numhandles:Ptr[Int]):Int = extern

  @name("curl_multi_info_read")
  def multi_info_read(multi:MultiCurl, message:Ptr[Int]):Ptr[CurlMessage] = extern // OMIT?

  @name("curl_multi_perform")
  def multi_perform(multi:MultiCurl, numhandles:Ptr[Int]):Int = extern // OMIT?

}

object CurlConstants {
  val URL:CurlOption = 10002
  val PORT:CurlOption = 10003
  val USERPASSWORD:CurlOption = 10005

  val READDATA:CurlOption = 10009
  val HEADERDATA:CurlOption = 10029
  val WRITEDATA:CurlOption = 10001

  val READCALLBACK:CurlOption = 20012
  val HEADERCALLBACK:CurlOption = 20079
  val WRITECALLBACK:CurlOption = 20011

  val TIMEOUT:CurlOption = 13
  val GET:CurlOption = 80
  val POST:CurlOption = 47
  val PUT:CurlOption = 54
  val CONTENTLENGTHDOWNLOADT:CurlInfo = 0x300000 + 15

  val PRIVATEDATA:CurlOption = 10103
  val GET_PRIVATEDATA:CurlInfo = 0x100000 + 21

  val SOCKETFUNCTION:CurlOption = 20001
  type SocketCallback = Function5[Curl, Ptr[Byte], CurlAction, Ptr[Byte], Ptr[Byte], CInt]
  val TIMERFUNCTION:CurlOption = 20004
  type TimerCallback = Function4[MultiCurl, Long, Ptr[Byte], CInt]

  type CurlAction = CInt
  val POLL_NONE:CurlAction = 0
  val POLL_IN:CurlAction = 1
  val POLL_OUT:CurlAction = 2
  val POLL_INOUT:CurlAction = 3
  val POLL_REMOVE:CurlAction = 4
   
}
