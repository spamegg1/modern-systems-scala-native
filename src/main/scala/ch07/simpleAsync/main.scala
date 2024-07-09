package ch07
package simpleAsync

import scala.concurrent.{ExecutionContext, Future}

@main
def simpleAsync: Unit =
  println("hello")
  given ExecutionContext = EventLoop // ch07.common.loop.scala
  println("setting up futures")
  Future(println("Future 1!")).map(_ => println("Future 2!"))
  EventLoop.run() // this was missing! The futures did not run without this.
  println("main about to return...")

// hello
// setting up futures
// uv_prepare_init returned 0
// uv_prepare_start returned 0
// Future 1!
// uv_prepare_start returned 0
// Future 2!
// stopping dispatcher
// uv_run returned 0
// main about to return...
