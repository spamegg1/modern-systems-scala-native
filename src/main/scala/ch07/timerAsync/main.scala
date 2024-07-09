package ch07
package timerAsync

import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext

@main
def timerAsync: Unit =
  println("hello")
  given ExecutionContext = EventLoop // ch07.common.loop.scala
  println("setting up timer")
  Timer.delay(2.seconds).map(_ => println("timer done!"))
  println("about to invoke loop.run()")
  EventLoop.run()
  println("done!")

// hello
// setting up timer
// uv_prepare_init returned 0
// uv_run returned 0
// about to invoke loop.run()
// callback fired!
// completing promise 1
// uv_prepare_start returned 0
// timer done!
// stopping dispatcher
// uv_run returned 0
// done!
