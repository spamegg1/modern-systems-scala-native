package `06asyncTimer`

import scala.scalanative.unsafe.*
import scala.scalanative.libc.*

import LibUV.*
import LibUVConstants.*

// @main
def asyncTimer(args: Array[String]): Unit =
  println("hello, world!")
  val loop = uv_default_loop()
  val timer = stdlib.malloc(uv_handle_size(UV_TIMER_T))

  val ret = uv_timer_init(loop, timer)
  println(s"uv_timer_init returned $ret")

  uv_timer_start(timer, timerCB, 1000, 0)

  println("invoking loop")
  uv_run(loop, UV_RUN_DEFAULT)

  println("done")

// val timerCB = new TimerCB:
val timerCB =
  CFuncPtr1.fromScalaFunction[TimerHandle, Unit]((handle: TimerHandle) =>
    println("timer fired!")
  )

@link("uv")
@extern
object LibUV:
  type TimerHandle = Ptr[Byte]
  type PipeHandle = Ptr[Ptr[Byte]]

  type Loop = Ptr[Byte]
  type TimerCB = CFuncPtr1[TimerHandle, Unit]

  def uv_default_loop(): Loop = extern
  def uv_loop_size(): CSize = extern
  def uv_is_active(handle: Ptr[Byte]): Int = extern
  def uv_handle_size(h_type: Int): CSize = extern
  def uv_req_size(r_type: Int): CSize = extern

  def uv_timer_init(loop: Loop, handle: TimerHandle): Int = extern
  def uv_timer_start(
      handle: TimerHandle,
      cb: TimerCB,
      timeout: Long,
      repeat: Long
  ): Int = extern
  def uv_timer_stop(handle: TimerHandle): Int = extern

  def uv_run(loop: Loop, runMode: Int): Int = extern

  def uv_strerror(err: Int): CString = extern
  def uv_err_name(err: Int): CString = extern

object LibUVConstants:
  import LibUV.*

  // uv_run_mode
  val UV_RUN_DEFAULT = 0
  val UV_RUN_ONCE = 1
  val UV_RUN_NOWAIT = 2

  // UV_HANDLE_T
  val UV_PIPE_T = 7 // Pipes
  val UV_POLL_T = 8 // Polling external sockets
  val UV_PREPARE_T = 9 // Runs every loop iteration
  val UV_PROCESS_T = 10 // Subprocess
  val UV_TCP_T = 12 // TCP sockets
  val UV_TIMER_T = 13 // Timer
  val UV_TTY_T = 14 // Terminal emulator
  val UV_UDP_T = 15 // UDP sockets

  // UV_REQ_T
  val UV_WRITE_REQ_T = 3

  val UV_READABLE = 1
  val UV_WRITABLE = 2
  val UV_DISCONNECT = 4
  val UV_PRIORITIZED = 8

  def check_error(v: Int, label: String): Int =
    if v == 0 then
      println(s"$label returned $v")
      v
    else
      val error = fromCString(uv_err_name(v))
      val message = fromCString(uv_strerror(v))
      println(s"$label returned $v: $error: $message")
      v
