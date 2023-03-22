/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
object EventLoop extends ExecutionContextExecutor {
  import LibUV._, LibUVConstants._
  val loop = uv_default_loop()
  private val taskQueue = ListBuffer[Runnable]()
  private val extensions = ListBuffer[LoopExtension]()

  private def dispatch(handle:PrepareHandle):Unit = {
    while (taskQueue.nonEmpty) {
      val runnable = taskQueue.remove(0)
      try {
        runnable.run()
      } catch {
        case t: Throwable => reportFailure(t)
      }
    }
    if (taskQueue.isEmpty) {
      LibUV.uv_prepare_stop(handle)
    }
  }

  private val dispatch_cb = CFunctionPtr.fromFunction1(dispatch)

  val handle = stdlib.malloc(uv_handle_size(UV_PREPARE_T))
  uv_prepare_init(loop, handle)
  uv_prepare_start(handle, dispatch_cb)

  def execute(runnable: Runnable): Unit =
    taskQueue += runnable

  def reportFailure(t: Throwable): Unit =
    t.printStackTrace()

  def run(mode:Int = UV_RUN_DEFAULT):Unit = {
    var continue = 1
    while (continue != 0) {
        continue = uv_run(loop, mode)
    }
  }
}

@extern
object LibUV {
  type Loop = Ptr[Byte]

  def uv_default_loop(): Loop = extern
  def uv_loop_size(): CSize = extern
  def uv_is_active(handle:Ptr[Byte]): Int = extern
  def uv_handle_size(h_type:Int): CSize = extern
  def uv_req_size(r_type:Int): CSize = extern

  def uv_timer_init(loop:Loop, handle:TimerHandle):Int = extern
  def uv_timer_start(handle:TimerHandle, cb:TimerCB, timeout:Long, repeat:Long):Int = extern
  def uv_timer_stop(handle:TimerHandle):Int = extern

  def uv_run(loop:Loop, runMode:Int): Int = extern

  def uv_strerror(err:Int): CString = extern
  def uv_err_name(err:Int): CString = extern

  type PrepareHandle = Ptr[Byte]
  type PrepareCB = CFunctionPtr1[PrepareHandle, Unit]

  def uv_prepare_init(loop:Loop, handle:PrepareHandle):Int = extern
  def uv_prepare_start(handle:PrepareHandle, cb: PrepareCB):Int = extern
  def uv_prepare_stop(handle:PrepareHandle):Unit = extern
}

object LibUVConstants {
  import LibUV._

  // uv_run_mode
  val UV_RUN_DEFAULT = 0
  val UV_RUN_ONCE = 1
  val UV_RUN_NOWAIT = 2

  // UV_HANDLE_T
  val UV_PIPE_T = 7
  val UV_POLL_T = 8
  val UV_PREPARE_T = 9
  val UV_PROCESS_T = 10
  val UV_TCP_T = 12
  val UV_TIMER_T = 13
  val UV_TTY_T = 14
  val UV_UDP_T = 15

  // UV_REQ_T
  val UV_WRITE_REQ_T = 3
  val UV_SHUTDOWN_REQ_T = 4

  val UV_READABLE = 1
  val UV_WRITABLE = 2
  val UV_DISCONNECT = 4
  val UV_PRIORITIZED = 8

  def check_error(v:Int, label:String):Int = {
      if (v == 0) {
        // println(s"$label returned $v")
        v
      } else {
        val error = fromCString(uv_err_name(v))
        val message = fromCString(uv_strerror(v))
        println(s"$label returned $v: $error: $message")
        v
      }
  }
}

