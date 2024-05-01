package ch06
package asyncTimer

import scala.scalanative.unsafe.CFuncPtr1
import scala.scalanative.libc.stdlib

import LibUV.*, LibUVConstants.{UV_TIMER_T, UV_RUN_DEFAULT}

@main
def run: Unit =
  println("hello, world!") // println is a blocking synchronous I/O function.
  val loop = uv_default_loop()
  val timer = stdlib.malloc(uv_handle_size(UV_TIMER_T))

  val ret = uv_timer_init(loop, timer)
  println(s"uv_timer_init returned $ret") // 0

  uv_timer_start(timer, timerCB, 1000, 0) // in miliseconds

  println("invoking loop")
  uv_run(loop, UV_RUN_DEFAULT)

  println("done")

val timerCB = CFuncPtr1.fromScalaFunction[TimerHandle, Unit]: (handle: TimerHandle) =>
  println("timer fired!")
