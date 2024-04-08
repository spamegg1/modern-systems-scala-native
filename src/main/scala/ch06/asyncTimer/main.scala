package ch06
package asyncTimer

import scala.scalanative.unsafe.CFuncPtr1
import scala.scalanative.libc.stdlib

import LibUV.{uv_default_loop, uv_timer_init, uv_timer_start, uv_run, uv_handle_size}
import LibUV.TimerHandle
import LibUVConstants.{UV_TIMER_T, UV_RUN_DEFAULT}

@main
def asyncTimer: Unit =
  println("hello, world!")
  val loop = uv_default_loop()
  val timer = stdlib.malloc(uv_handle_size(UV_TIMER_T))

  val ret = uv_timer_init(loop, timer)
  println(s"uv_timer_init returned $ret")

  uv_timer_start(timer, timerCB, 1000, 0)

  println("invoking loop")
  uv_run(loop, UV_RUN_DEFAULT)

  println("done")

val timerCB = CFuncPtr1.fromScalaFunction[TimerHandle, Unit]: (handle: TimerHandle) =>
  println("timer fired!")
