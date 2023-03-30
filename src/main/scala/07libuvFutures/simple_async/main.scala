package `07simpleAsync`

import scala.scalanative.unsafe.*
import scala.scalanative.libc.*
import scala.concurrent.*

// @main
def simpleAsync(args: String*): Unit =
  println("hello")
  implicit val loop = EventLoop
  println("setting up futures")
  Future {
    println("Future 1!")
  }.map { _ =>
    println("Future 2!")
  }
  println("main about to return...")
