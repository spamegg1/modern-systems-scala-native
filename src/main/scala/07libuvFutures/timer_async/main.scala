package `07timerAsync`

import scala.scalanative.unsafe.*
import scala.scalanative.libc.*
import scala.concurrent.duration.*

// @main
def timerAsync(args: String*): Unit =
  println("hello")
  implicit val loop = EventLoop
  println("setting up timer")
  Timer.delay(2.seconds).map { _ => println("timer done!") }
  println("about to invoke loop.run()")
  loop.run()
  println("done!")
