package ch07.timerAsync

import scalanative.unsafe.*
import scalanative.libc.stdlib
import collection.mutable
import concurrent.{ExecutionContext, ExecutionContextExecutor}
import concurrent.Future
import concurrent.Promise
import util.{Try, Success}
import concurrent.duration.*
import LibUV.*, LibUVConstants.*

object Timer:
  var serial = 0L
  val timers = mutable.HashMap[Long, Promise[Unit]]()

  def delay(dur: Duration): Future[Unit] =
    val promise = Promise[Unit]()
    serial += 1
    val timerId = serial
    timers(timerId) = promise
    val millis = dur.toMillis

    val timerHandle = stdlib.malloc(uv_handle_size(UV_TIMER_T))
    uv_timer_init(EventLoop.loop, timerHandle)

    val timerData = timerHandle.asInstanceOf[Ptr[Long]]
    !timerData = timerId
    uv_timer_start(timerHandle, timerCB, millis, 0)

    promise.future

  val timerCB = CFuncPtr1.fromScalaFunction[TimerHandle, Unit]: (handle: TimerHandle) =>
    println("callback fired!")
    val timerData = handle.asInstanceOf[Ptr[Long]]
    val timerId = !timerData
    val timerPromise = timers(timerId)
    timers.remove(timerId)
    println(s"completing promise ${timerId}")
    timerPromise.success(())
