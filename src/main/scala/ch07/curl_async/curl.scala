package ch07.curlAsync

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.*
import collection.mutable
import scalanative.libc.stdlib.*
import scalanative.libc.string.*
import concurrent.*
import scalanative.runtime.Boxes
import scalanative.runtime.Intrinsics

case class ResponseState(
    var code: Int = 200,
    var headers: mutable.Map[String, String] = mutable.Map(),
    var body: String = ""
)

object Curl:
  import LibCurl.*
  import LibCurlConstants.*
  import LibUV.*
  import LibUVConstants.*

  var serial = 0L

  val loop = EventLoop.loop
  var multi: MultiCurl = null
  val timerHandle: TimerHandle = malloc(uv_handle_size(UV_TIMER_T))

  val requestPromises = mutable.Map[Long, Promise[ResponseState]]()
  val requests = mutable.Map[Long, ResponseState]()

  var initialized = false

  def init: Unit =
    if !initialized then
      println("initializing curl")
      global_init(1)

      multi = multi_init()
      println(s"initilized multiHandle $multi")

      println("socket function")
      val setopt_r_1 = multi_setopt_ptr(multi, SOCKETFUNCTION, func_to_ptr(socketCB))

      println("timer function")
      val setopt_r_2 = multi_setopt_ptr(multi, TIMERFUNCTION, func_to_ptr(startTimerCB))

      println(s"timerCB: $startTimerCB")
      check(uv_timer_init(loop, timerHandle), "uv_timer_init")

      initialized = true
      println("done")

  def addHeaders(curl: Curl, headers: Seq[String]): Ptr[CurlSList] =
    var slist: Ptr[CurlSList] = null
    for h <- headers do addHeader(slist, h)
    curl_easy_setopt(curl, HTTPHEADER, slist.asInstanceOf[Ptr[Byte]])
    slist

  def addHeader(slist: Ptr[CurlSList], header: String): Ptr[CurlSList] = Zone:
    slist_append(slist, toCString(header)) // 0.5

  def startRequest(
      method: Int,
      url: String,
      headers: Seq[String] = Seq.empty,
      body: String = ""
  ): Future[ResponseState] = Zone:
    init
    val curlHandle = easy_init()
    serial += 1
    val reqId = serial
    println(s"initializing handle $curlHandle for request $reqId")
    val req_id_ptr = malloc(sizeof[Long]).asInstanceOf[Ptr[Long]]
    !req_id_ptr = reqId
    requests(reqId) = ResponseState()
    val promise = Promise[ResponseState]()
    requestPromises(reqId) = promise

    method match
      case GET =>
        check(curl_easy_setopt(curlHandle, URL, toCString(url)), "easy_setopt")
        check(
          curl_easy_setopt(curlHandle, WRITECALLBACK, func_to_ptr(dataCB)),
          "easy_setopt"
        )
        check(
          curl_easy_setopt(
            curlHandle,
            WRITEDATA,
            req_id_ptr.asInstanceOf[Ptr[Byte]]
          ),
          "easy_setopt"
        )
        check(
          curl_easy_setopt(curlHandle, HEADERCALLBACK, func_to_ptr(headerCB)),
          "easy_setopt"
        )
        check(
          curl_easy_setopt(
            curlHandle,
            HEADERDATA,
            req_id_ptr.asInstanceOf[Ptr[Byte]]
          ),
          "easy_setopt"
        )
        check(
          curl_easy_setopt(
            curlHandle,
            PRIVATEDATA,
            req_id_ptr.asInstanceOf[Ptr[Byte]]
          ),
          "easy_setopt"
        )

    multi_add_handle(multi, curlHandle)

    println("request initialized")
    promise.future

  val dataCB = CFuncPtr4.fromScalaFunction[Ptr[Byte], CSize, CSize, Ptr[Byte], CSize]:
    (ptr: Ptr[Byte], size: CSize, nmemb: CSize, data: Ptr[Byte]) =>
      val serial = !(data.asInstanceOf[Ptr[Long]])
      val len = stackalloc[Double]()
      !len = 0
      val strData = bufferToString(ptr, size, nmemb)
      println(s"req $serial: got data of size ${size} x ${nmemb}")

      val resp = requests(serial)
      resp.body = resp.body + strData
      requests(serial) = resp

      size * nmemb

  val headerCB = CFuncPtr4.fromScalaFunction[Ptr[Byte], CSize, CSize, Ptr[Byte], CSize]:
    (ptr: Ptr[Byte], size: CSize, nmemb: CSize, data: Ptr[Byte]) =>
      val serial = !(data.asInstanceOf[Ptr[Long]])
      val len = stackalloc[Double]()
      !len = 0
      val strData = bufferToString(ptr, size, nmemb)
      println(s"req $serial: got header line of size ${size} x ${nmemb}")

      val resp = requests(serial)
      resp.body = resp.body + strData
      requests(serial) = resp

      size * nmemb

  val socketCB =
    CFuncPtr5.fromScalaFunction[Curl, Ptr[Byte], CInt, Ptr[Byte], Ptr[Byte], CInt]:
      (
          curl: Curl,
          socket: Ptr[Byte],
          action: Int,
          data: Ptr[Byte],
          socket_data: Ptr[Byte]
      ) =>
        println(s"socketCB called with action ${action}")

        val pollHandle =
          if socket_data == null then
            println(s"initializing handle for socket ${socket}")
            val buf = malloc(uv_handle_size(UV_POLL_T)).asInstanceOf[Ptr[Ptr[Byte]]]
            !buf = socket
            check(uv_poll_init_socket(loop, buf, socket), "uv_poll_init_socket")
            check(
              multi_assign(multi, socket, buf.asInstanceOf[Ptr[Byte]]),
              "multi_assign"
            )
            buf
          else socket_data.asInstanceOf[Ptr[Ptr[Byte]]]

        val events = action match
          case POLL_NONE   => None
          case POLL_IN     => Some(UV_READABLE)
          case POLL_OUT    => Some(UV_WRITABLE)
          case POLL_INOUT  => Some(UV_READABLE | UV_WRITABLE)
          case POLL_REMOVE => None

        events match
          case Some(ev) =>
            println(s"starting poll with events $ev")
            uv_poll_start(pollHandle, ev, pollCB)
          case None =>
            println("stopping poll")
            uv_poll_stop(pollHandle)
            startTimerCB(multi, 1, null)
        0

  val pollCB = CFuncPtr3.fromScalaFunction[PollHandle, Int, Int, Unit]:
    (pollHandle: PollHandle, status: Int, events: Int) =>
      println(s"ready_for_curl fired with status ${status} and events ${events}")
      val socket = !(pollHandle.asInstanceOf[Ptr[Ptr[Byte]]])
      val actions = (events & 1) | (events & 2)
      val running_handles = stackalloc[Int]()
      val result = multi_socket_action(multi, socket, actions, running_handles)
      println(s"multi_socket_action ${result}")

  val startTimerCB = CFuncPtr3.fromScalaFunction[MultiCurl, Long, Ptr[Byte], CInt]:
    (curl: MultiCurl, timeout_ms: Long, data: Ptr[Byte]) =>
      println(s"start_timer called with timeout ${timeout_ms} ms")
      val time =
        if (timeout_ms < 1) then
          println("setting effective timeout to 1")
          1
        else timeout_ms
      println("starting timer")
      check(uv_timer_start(timerHandle, timeoutCB, time, 0), "uv_timer_start")
      cleanup_requests()
      0

  val timeoutCB = CFuncPtr1.fromScalaFunction[TimerHandle, Unit]: (handle: TimerHandle) =>
    println("in timeout callback")
    val running_handles = stackalloc[Int]()
    multi_socket_action(multi, int_to_ptr(-1), 0, running_handles)
    println(s"on_timer fired, ${!running_handles} sockets running")

  def cleanup_requests(): Unit =
    val messages = stackalloc[Int]()
    val privateDataPtr = stackalloc[Ptr[Long]]()
    var message: Ptr[CurlMessage] = multi_info_read(multi, messages)

    while message != null do
      println(s"""Got a message ${message._1} from multi_info_read,
              ${!messages} left in queue""")
      val handle: Curl = message._2
      check(
        easy_getinfo(
          handle,
          GET_PRIVATEDATA,
          privateDataPtr.asInstanceOf[Ptr[Byte]]
        ),
        "getinfo"
      )
      val privateData = !privateDataPtr
      val reqId = !privateData
      val reqData = requests.remove(reqId).get
      val promise = Curl.requestPromises.remove(reqId).get
      promise.success(reqData)
      message = multi_info_read(multi, messages)

    println("done handling messages")

  def bufferToString(ptr: Ptr[Byte], size: CSize, nmemb: CSize): String =
    val byteSize = size * nmemb
    val buffer = malloc(byteSize + 1.toUSize) // 0.5
    strncpy(buffer, ptr, byteSize + 1.toUSize) // 0.5
    val res = fromCString(buffer)
    free(buffer)
    res

  def multi_setopt(curl: MultiCurl, option: CInt, parameters: CVarArg*): Int = Zone:
    curl_multi_setopt(curl, option, toCVarArgList(parameters.toSeq))

  def easy_setopt(curl: Curl, option: CInt, parameters: CVarArg*): Int = Zone:
    curl_easy_setopt(curl, option, toCVarArgList(parameters.toSeq))

  def func_to_ptr(f: Object): Ptr[Byte] = Boxes.boxToPtr[Byte](Boxes.unboxToCFuncPtr1(f))

  def int_to_ptr(i: Int): Ptr[Byte] = Boxes.boxToPtr[Byte](Intrinsics.castIntToRawPtr(i))

  def long_to_ptr(l: Long): Ptr[Byte] =
    Boxes.boxToPtr[Byte](Intrinsics.castLongToRawPtr(l))

object LibCurlConstants:
  import LibCurl.*
  val URL = 10002
  val PORT = 10003
  val USERPASSWORD = 10005

  val READDATA = 10009
  val HEADERDATA = 10029
  val WRITEDATA = 10001

  val READCALLBACK = 20012
  val HEADERCALLBACK = 20079
  val WRITECALLBACK = 20011

  val TIMEOUT = 13
  val GET = 80
  val POST = 47
  val PUT = 54
  val CONTENTLENGTHDOWNLOADT = 0x300000 + 15
  val HTTPHEADER = 10023

  val PRIVATEDATA = 10103
  val GET_PRIVATEDATA = 0x100000 + 21

  val SOCKETFUNCTION = 20001
  type SocketCallback =
    CFuncPtr5[Curl, Ptr[Byte], CInt, Ptr[Byte], Ptr[Byte], CInt]
  val TIMERFUNCTION = 20004
  type TimerCallback = CFuncPtr3[MultiCurl, Long, Ptr[Byte], CInt]

  type CurlAction = CInt
  val POLL_NONE: CurlAction = 0
  val POLL_IN: CurlAction = 1
  val POLL_OUT: CurlAction = 2
  val POLL_INOUT: CurlAction = 3
  val POLL_REMOVE: CurlAction = 4

@link("curl")
@extern
object LibCurl:
  type Curl = Ptr[Byte]
  type CurlBuffer = CStruct2[CString, CSize]
  type CurlOption = Int
  type CurlRequest = CStruct4[Ptr[Byte], Long, Long, Int]
  type CurlMessage = CStruct3[Int, Curl, Ptr[Byte]]

  type CurlDataCallback = CFuncPtr4[Ptr[Byte], CSize, CSize, Ptr[Byte], CSize]
  type CurlSocketCallback = CFuncPtr5[Curl, Ptr[Byte], CInt, Ptr[Byte], Ptr[Byte], CInt]
  type CurlTimerCallback = CFuncPtr3[MultiCurl, Long, Ptr[Byte], CInt]

  @name("curl_global_init")
  def global_init(flags: Long): Unit = extern

  @name("curl_global_cleanup")
  def global_cleanup(): Unit = extern

  @name("curl_easy_init")
  def easy_init(): Curl = extern

  @name("curl_easy_cleanup")
  def easy_cleanup(handle: Curl): Unit = extern

  @name("curl_easy_setopt")
  def curl_easy_setopt(handle: Curl, option: CInt, parameter: CVarArgList): CInt = extern

  @name("curl_easy_setopt")
  def curl_easy_setopt(handle: Curl, option: CInt, parameter: Ptr[Byte]): CInt = extern

  @name("curl_easy_getinfo")
  def easy_getinfo(handle: Curl, info: CInt, parameter: Ptr[Byte]): CInt = extern

  @name("curl_easy_perform")
  def easy_perform(easy_handle: Curl): CInt = extern

  type MultiCurl = Ptr[Byte]

  @name("curl_multi_init")
  def multi_init(): MultiCurl = extern

  @name("curl_multi_add_handle")
  def multi_add_handle(multi: MultiCurl, easy: Curl): Int = extern

  @name("curl_multi_setopt")
  def curl_multi_setopt(
      multi: MultiCurl,
      option: CInt,
      parameter: CVarArg
  ): CInt = extern

  @name("curl_multi_setopt")
  def multi_setopt_ptr(
      multi: MultiCurl,
      option: CInt,
      parameter: Ptr[Byte]
  ): CInt = extern

  @name("curl_multi_assign")
  def multi_assign(
      multi: MultiCurl,
      socket: Ptr[Byte],
      socket_data: Ptr[Byte]
  ): Int = extern

  @name("curl_multi_socket_action")
  def multi_socket_action(
      multi: MultiCurl,
      socket: Ptr[Byte],
      events: Int,
      numhandles: Ptr[Int]
  ): Int = extern

  @name("curl_multi_info_read")
  def multi_info_read(multi: MultiCurl, message: Ptr[Int]): Ptr[CurlMessage] = extern

  @name("curl_multi_perform")
  def multi_perform(multi: MultiCurl, numhandles: Ptr[Int]): Int = extern

  @name("curl_multi_cleanup")
  def multi_cleanup(multi: MultiCurl): Int = extern

  type CurlSList = CStruct2[Ptr[Byte], CString]

  @name("curl_slist_append")
  def slist_append(slist: Ptr[CurlSList], string: CString): Ptr[CurlSList] = extern

  @name("curl_slist_free_all")
  def slist_free_all(slist: Ptr[CurlSList]): Unit = extern

  def curl_easy_strerror(code: Int): CString = extern
