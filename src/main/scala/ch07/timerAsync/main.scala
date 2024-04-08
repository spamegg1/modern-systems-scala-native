package ch07.timerAsync

import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext

@main
def timerAsync(args: String*): Unit =
  println("hello")
  given ExecutionContext = EventLoop
  println("setting up timer")
  Timer.delay(2.seconds).map(_ => println("timer done!"))
  println("about to invoke loop.run()")
  EventLoop.run()
  println("done!")
