package ch08
package syncPipe

import scala.util.{Try, Success, Failure}
import ch07.LibUV.uv_run, ch07.LibUVConstants.UV_RUN_DEFAULT

@main
def run: Unit =
  println("hello!")
  val p = SyncPipe(0)
  val q = p
    .map: d =>
      println(s"consumed $d")
      d
    .map: d =>
      val parsed = Try(d.toInt)
      println(s"parsed: $parsed")
      parsed
    .map:
      case Success(i) => println(s"saw number $i")
      case Failure(f) => println(s"error: $f")

  uv_run(ch07.EventLoop.loop, UV_RUN_DEFAULT)
  println("done")

// 1
// read 2 bytes from pipe 0
// consumed 1
// parsed: Success(1)
// saw number 1
// 2
// read 2 bytes from pipe 0
// consumed 2
// parsed: Success(2)
// saw number 2
// foo
// read 4 bytes from pipe 0
// consumed foo
// parsed: Failure(java.lang.NumberFormatException: For input string: "foo")
// error: java.lang.NumberFormatException: For input string: "foo"
// ^C
