package ch08

import scalanative.unsafe.CQuote
import scala.util.Try

object FileInputPipeExample:
  import ch07.LibUV.*, ch07.LibUVConstants.*
  import filePipe.FilePipe, filePipeOut.FileOutputPipe

  @main
  def fileInputPipe: Unit =
    val p = FilePipe
      .apply(c"./data.txt")
      .map: d =>
        println(s"consumed $d")
        d
      .map: d =>
        val parsed = Try(d.toInt)
        println(s"parsed: $parsed")
        parsed
    // .addDestination(FileOutputPipe(c"./output.txt", false))
    uv_run(ch07.EventLoop.loop, UV_RUN_DEFAULT)
    println("done")

object FileOutputPipeExample:
  import ch07.LibUV.*, ch07.LibUVConstants.*
  import filePipe.FilePipe, filePipeOut.FileOutputPipe

  @main
  def fileOutputPipe: Unit =
    println("hello!")
    // val p = SyncPipe(0)
    val p = FilePipe.apply(c"./data.txt")

    val q = p
      .map: d =>
        println(s"consumed $d")
        d
      .map: d =>
        val parsed = Try(d.toInt)
        println(s"parsed: $parsed")
        parsed.toString
    // .addDestination(FileOutputPipe(c"./output.txt", false))
    uv_run(ch07.EventLoop.loop, UV_RUN_DEFAULT)
    println("done")

// object Stuff:
//   import filePipe.*
//   import ch07.LibUV.*, ch07.LibUVConstants.*

//   def filter[T](f: T => Boolean): Pipe[T] =
//     addDestination(
//       mapOption: t =>
//         f(t) match
//           case true  => Some(t)
//           case false => None
//     )

//   @main
//   def stuff: Unit =
//     val p1: Pipe[String, String] = ???
//     var counter = 0
//     p1.map: i =>
//       counter += 1
//       i

//     // ...
//     uv_run(ch07.EventLoop.loop, UV_RUN_DEFAULT)
//     println(s"saw $counter elements")

//     val p2: Pipe[String] = ???
//     val c = p2.addDestination(Counter())
//     uv_run(ch07.EventLoop.loop, UV_RUN_DEFAULT)
//     println(s"saw ${c.counter} elements")

//     val p3: Pipe[String] = ???
//     p3.mapConcat(content => content.split("\n"))
//       .mapConcat(line => line.split(" "))
//       .map(word => println(s"saw word: ${word}"))

//     uv_run(ch07.EventLoop.loop, UV_RUN_DEFAULT)
//     println(s"saw ${c.counter} elements")

//     SyncPipe(0)
//       .map(d =>
//         println(s"consumed $d")
//         d
//       )
//       .map(d =>
//         val parsed = Try {
//           d.toInt
//         }
//       )
//       .filter {
//         case Success(i) =>
//           println(s"saw number $i")
//           true
//         case Failure(f) =>
//           println(s"error: $f")
//           false
//       }
//     // ...

//     val p4: Pipe[String] = ???
//     p4.mapAsync(url => Curl.get(url))
//       .map(response => println(s"got back result: $response"))
