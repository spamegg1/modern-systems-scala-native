package ch07

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.*
import scalanative.runtime.{Boxes, Intrinsics}
import scalanative.libc.stdlib.{malloc, free}
import scalanative.libc.string.strncpy

import collection.mutable.{Map => MMap}
import concurrent.{Future, Promise}

object Curl:
  import LibCurl.*, LibCurlConstants.*
  import LibUV.*, LibUVConstants.*

  def funcToPtr(f: Object): Ptr[Byte] = Boxes.boxToPtr[Byte](Boxes.unboxToCFuncPtr1(f))
  def intToPtr(i: Int): Ptr[Byte] = Boxes.boxToPtr[Byte](Intrinsics.castIntToRawPtr(i))
  def longToPtr(l: Long): Ptr[Byte] = Boxes.boxToPtr[Byte](Intrinsics.castLongToRawPtr(l))

  var serial = 0L
  val loop = uv_default_loop()

  var multi: MultiCurl = null
  val timerHandle: TimerHandle = malloc(uv_handle_size(UV_TIMER_T))

  val requestPromises = MMap[Long, Promise[ResponseState]]()
  val requests = MMap[Long, ResponseState]()

  var initialized = false

  def init: Unit =
    if !initialized then
      println("initializing curl")
      global_init(1)

      multi = multi_init()
      println(s"initilized multiHandle $multi")

      println("socket function")
      val setopt_r_1 = multi_setopt_ptr(multi, SOCKETFUNCTION, funcToPtr(socketCB))

      println("timer function")
      val setopt_r_2 = multi_setopt_ptr(multi, TIMERFUNCTION, funcToPtr(startTimerCB))

      println(s"timerCB: $startTimerCB")
      checkError(uv_timer_init(loop, timerHandle), "uv_timer_init")

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
  ): Future[ResponseState] =
    Zone:
      init
      val curlHandle = easy_init()
      serial += 1
      val reqId = serial
      println(s"initializing handle $curlHandle for request $reqId")
      val reqIdPtr = malloc(sizeof[Long]).asInstanceOf[Ptr[Long]]
      !reqIdPtr = reqId
      requests(reqId) = ResponseState()
      val promise = Promise[ResponseState]()
      requestPromises(reqId) = promise

      method match
        case GET =>
          checkError(curl_easy_setopt(curlHandle, URL, toCString(url)), "easy_setopt")
          checkError(
            curl_easy_setopt(curlHandle, WRITECALLBACK, funcToPtr(dataCB)),
            "easy_setopt"
          )
          checkError(
            curl_easy_setopt(curlHandle, WRITEDATA, reqIdPtr.asInstanceOf[Ptr[Byte]]),
            "easy_setopt"
          )
          checkError(
            curl_easy_setopt(curlHandle, HEADERCALLBACK, funcToPtr(headerCB)),
            "easy_setopt"
          )
          checkError(
            curl_easy_setopt(curlHandle, HEADERDATA, reqIdPtr.asInstanceOf[Ptr[Byte]]),
            "easy_setopt"
          )
          checkError(
            curl_easy_setopt(curlHandle, PRIVATEDATA, reqIdPtr.asInstanceOf[Ptr[Byte]]),
            "easy_setopt"
          )

      multi_add_handle(multi, curlHandle)

      println("request initialized")
      promise.future

  val dataCB = CFuncPtr4.fromScalaFunction[Ptr[Byte], CSize, CSize, Ptr[Byte], CSize]:
    (ptr: Ptr[Byte], size: CSize, nmemb: CSize, data: Ptr[Byte]) =>
      val serial = !(data.asInstanceOf[Ptr[Long]])
      val len = stackalloc[Double](1)
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
      val len = stackalloc[Double](1)
      !len = 0
      val strData = bufferToString(ptr, size, nmemb)
      println(s"req $serial: got header line of size ${size} x ${nmemb}")

      val resp = requests(serial)
      resp.body = resp.body + strData
      requests(serial) = resp

      size * nmemb

  val socketCB: CurlSocketCallback = CFuncPtr5.fromScalaFunction:
    (
        curl: Curl,
        socket: Ptr[Byte],
        action: CurlAction,
        data: Ptr[Byte],
        socketData: Ptr[Byte]
    ) =>
      println(s"socketCB called with action ${action}")

      val pollHandle: PollHandle =
        if socketData == null then
          println(s"initializing handle for socket ${socket}")
          val buf = malloc(uv_handle_size(UV_POLL_T)).asInstanceOf[PollHandle]
          !buf = socket
          checkError(uv_poll_init_socket(loop, buf, socket), "uv_poll_init_socket")
          checkError(
            multi_assign(multi, socket, buf.asInstanceOf[Ptr[Byte]]),
            "multi_assign"
          )
          buf
        else socketData.asInstanceOf[PollHandle]

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

  // libuv callback for PollHandle, used above in socket callback
  val pollCB: PollCB = CFuncPtr3.fromScalaFunction:
    (pollHandle: PollHandle, status: Int, events: Int) =>
      println(s"ready_for_curl fired with status ${status} and events ${events}")
      val socket = !pollHandle
      val actions = (events & 1) | (events & 2)
      val runningHandles = stackalloc[Int](1) // number of sockets
      val result = multi_socket_action(multi, socket, actions, runningHandles)
      println(s"multi_socket_action ${result}")

  // passed to libcurl as TIMERFUNCTION and controls a libuv TimerHandle
  val startTimerCB: CurlTimerCallback = CFuncPtr3.fromScalaFunction:
    (curl: MultiCurl, timeoutMs: Long, data: Ptr[Byte]) =>
      println(s"start_timer called with timeout ${timeoutMs} ms")
      val time = // libcurl uses set_timeout in two ways
        if timeoutMs < 1 then // either to tell libuv to call multi_socket_action NOW
          println("setting effective timeout to 1")
          1
        else timeoutMs // or at some point in the future
      println("starting timer")
      checkError(uv_timer_start(timerHandle, timeoutCB, time, 0), "uv_timer_start")
      cleanupRequests
      0

  // when timeout is reached, libuv should call multi_socket_action
  val timeoutCB: TimerCB = CFuncPtr1.fromScalaFunction: (handle: TimerHandle) =>
    println("in timeout callback")
    val runningHandles = stackalloc[Int](1) // number of sockets
    multi_socket_action(multi, intToPtr(-1), 0, runningHandles)
    println(s"on_timer fired, ${!runningHandles} sockets running")

  def cleanupRequests: Unit =
    val messages = stackalloc[Int](1)
    val privateDataPtr = stackalloc[Ptr[Long]](1)
    var message: Ptr[CurlMessage] = multi_info_read(multi, messages)

    while message != null do
      println(s"Got a message ${message._1} from multi_info_read,")
      println(s"${!messages} left in queue")
      val handle: Curl = message._2

      checkError(
        easy_getinfo(handle, GET_PRIVATEDATA, privateDataPtr.asInstanceOf[Ptr[Byte]]),
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

  def multiSetopt(curl: MultiCurl, option: CInt, parameters: CVarArg*): Int = Zone:
    curl_multi_setopt(curl, option, toCVarArgList(parameters.toSeq))

  def easySetopt(curl: Curl, option: CInt, parameters: CVarArg*): Int = Zone:
    curl_easy_setopt(curl, option, toCVarArgList(parameters.toSeq))

  def get(url: CString): Future[String] = ??? // dummy to make examples/asyncMain work
