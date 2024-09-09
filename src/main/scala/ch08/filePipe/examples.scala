package ch08
package filePipe
package examples

import scalanative.unsafe.{CQuote, CString}
import scala.util.{Try, Success, Failure}
import ch07.LibUV.*, ch07.LibUVConstants.*
import ch07.examples.ExecutionContext
import ch07.EventLoop

@main
def fileInputPipeExample: Unit =
  val path = c"../data.txt" // replace this with your own path
  val p = FilePipe(path)
    .map: d =>
      println(s"consumed $d")
      val parsed = Try(d.toInt)
      println(s"parsed: $parsed")
      parsed.toString // I changed this to make it type-check.
    .addDestination(FileOutputPipe(c"./output.txt", false))

  uv_run(ch07.EventLoop.loop, UV_RUN_DEFAULT)
  println("done")

@main
def fileOutputPipeExample: Unit =
  println("hello!")
  val p = FilePipe(c"../data.txt")
    .map: d =>
      println(s"consumed $d")
      val parsed = Try(d.toInt)
      println(s"parsed: $parsed")
      parsed.toString
    .addDestination(FileOutputPipe(c"./output.txt", false))

  uv_run(ch07.EventLoop.loop, UV_RUN_DEFAULT)
  println("done")

@main
def asyncPipeExample: Unit =
  val p4: Pipe[String, CString] = ???
  p4.mapAsync(url => ch07.Curl.get(url))(using EventLoop)
    .map(response => println(s"got back result: $response"))

@main
def statefulProcessorExample: Unit =
  val p1: Pipe[String, String] = ???
  var counter = 0
  p1.map: i =>
    counter += 1
    i
  // ...
  uv_run(ch07.EventLoop.loop, UV_RUN_DEFAULT)
  println(s"saw $counter elements")

@main
def counterSinkExample: Unit =
  // val p2: Pipe[String, String] = ??? // not sure how to make this work.
  // val c = p2.addDestination(CounterSink())
  val c = CounterSink()
  uv_run(ch07.EventLoop.loop, UV_RUN_DEFAULT)
  println(s"saw ${c.counter} elements")

@main
def tokenizerExample: Unit =
  val p3: Pipe[String, String] = ???
  p3.mapConcat(content => content.split("\n"))
    .mapConcat(line => line.split(" "))
    .map(word => println(s"saw word: ${word}"))

  uv_run(ch07.EventLoop.loop, UV_RUN_DEFAULT)
  // println(s"saw ${c.counter} elements")
