package ch07
package examples

import scalanative.unsafe.*
import scalanative.libc.{stdlib, stdio}

// This code is not meant to compile and run, it's conceptual pseudocode.
// I just made up some stuff to make it compile.

object curlMulti:
  import LibCurl.*, LibCurlConstants.*, LibUV.*, LibUVConstants.*

  var multi = stackalloc[Byte](1) // made up!
  var initialized = false // made up!
  var loop = uv_default_loop() // made up!
  var timer_handle = stdlib.malloc(uv_handle_size(UV_TIMER_T)) // made up!

  // START: onPoll
  def onPoll(pollHandle: PollHandle, status: Int, events: Int): Unit =
    val socket = !(pollHandle.asInstanceOf[Ptr[Ptr[Byte]]])
    val actions = (events & 1) | (events & 2) // Whoa, nelly!
    val running_handles = stackalloc[Int](1)
    val result = multi_socket_action(multi, socket, actions, running_handles) // check

  def on_timer(handle: TimerHandle): Unit =
    val running_handles = stackalloc[Int](1)
    multi_socket_action(multi, -1.asInstanceOf[Ptr[Byte]], 0, running_handles)
    // logger.debug(s"on_timer fired, ${!running_handles} sockets running") // ???

  // val writeCB = CFuncPtr4.fromScalaFunction(writeData) // no idea what these are
  // val headerCB = CFuncPtr4.fromScalaFunction(writeHeader)
  // val readyCB = CFuncPtr3.fromScalaFunction(ready_for_curl)
  val timerCB = CFuncPtr1.fromScalaFunction(on_timer)

  def socket_state_change(
      curl: Curl,
      socket: Ptr[Byte],
      action: Int,
      data: Ptr[Byte],
      socket_data: Ptr[Byte]
  ): Int =
    // logger.debug(s"handle_socket called with action ${action}")
    // logger.trace(c"\tdata %p, socket %p, action %d\n", socket, socket_data, action)
    val pollHandle =
      if socket_data == null then
        // logger.debug("uninitialized poll_handle")
        val buf = stdlib.malloc(uv_handle_size(UV_POLL_T)).asInstanceOf[Ptr[Ptr[Byte]]]
        !buf = socket
        checkError(
          uv_poll_init_socket(EventLoop.loop, buf, socket),
          "uv_poll_init_socket"
        )
        // debug_check("multi_assign", multi_assign(multi, socket, buf.cast[Ptr[Byte]]))
        stdio.printf(
          c"\t assigned data %x containing socket %x to socket %x\n",
          buf,
          !buf,
          socket
        )
        buf
      else
        // logger.trace("poll_handle exists")
        socket_data.asInstanceOf[Ptr[Ptr[Byte]]]

    val events = action match
      case POLL_NONE   => None
      case POLL_IN     => Some(UV_READABLE)
      case POLL_OUT    => Some(UV_WRITABLE)
      case POLL_INOUT  => Some(UV_READABLE | UV_WRITABLE)
      case POLL_REMOVE => None

    events match
      case Some(ev) =>
      // logger.info(s"starting poll with events $ev")
      // uv_poll_start(pollHandle, ev, readyCB)
      case None =>
        // logger.warn("stopping poll")
        uv_poll_stop(pollHandle)
        // start_timer(multi, 1, null)
        uv_timer_start(timer_handle, timerCB, 1, 0)
        stdlib.free(pollHandle)
    0

  def cleanup_requests(): Unit =
    val messages = stackalloc[Int]()
    val privateDataPtr = stackalloc[Ptr[CurlRequest]](1)
    var message: Ptr[CurlMessage] = multi_info_read(multi, messages)
    while message != null do
      // logger.info(s"""Got a message ${!message._1} from multi_info_read,
      // ${!messages} left in queue""")
      val handle = !message._2
      // trace_check("easy_getinfo", easy_getinfo(handle, GET_PRIVATEDATA, privateDataPtr))
      val privateData = !privateDataPtr
      // Curl.complete_request(!privateDataPtr)
      message = multi_info_read(multi, messages)

    // logger.debug("done handling messages")

  def shutdown(): Unit =
    // debug_check("cleanup", multi_cleanup(multi))
    global_cleanup()
    initialized = false

  def set_timeout(curl: MultiCurl, timeout_ms: Long, data: Ptr[Byte]): Int =
    // logger.info(s"start_timer called with timeout ${timeout_ms} ms")
    val time =
      if timeout_ms < 1 then
        // logger.warn("setting effective timeout to 1")
        1
      else timeout_ms
    stdio.printf(c"timer handle is %p, timer cb is %p\n", timer_handle, timerCB)
    checkError(uv_timer_start(timer_handle, timerCB, time, 0), "uv_timer_start")
    cleanup_requests()
    0

  // val socketCB = CFuncPtr5.fromScalaFunction(handle_socket)
  // val curltimerCB = CFuncPtr1.fromScalaFunction(start_timer)

  def init(): Unit =
    if initialized == false then
      loop = uv_default_loop()
      global_init(1)
      multi = multi_init()
      // val setopt_r_1 = curl_multi_setopt(multi, SOCKETFUNCTION, socketCB)
      // logger.debug(s"multi_setopt SOCKETFUNCTION ${setopt_r_1}")
      // val setopt_r_2 = curl_multi_setopt(multi, TIMERFUNCTION, curltimerCB)
      // logger.debug(s"multi_setopt TIMERFUNCTION ${setopt_r_2}")

      timer_handle = stdlib.malloc(uv_handle_size(UV_TIMER_T))
      // checkError(uv_timer_init(loop, timer_handle), "uv_timer_init")
      // logger.debug("CURL INITIALIZED")
      initialized = true
