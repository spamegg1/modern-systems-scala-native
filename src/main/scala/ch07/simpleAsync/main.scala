package ch07.simpleAsync

import scala.concurrent.{ExecutionContext, Future}

@main
def simpleAsync(args: String*): Unit =
  println("hello")
  given ExecutionContext = EventLoop
  println("setting up futures")
  Future { println("Future 1!") }.map { _ => println("Future 2!") }
  println("main about to return...")
