package ch08
package syncPipe

import scala.util.{Try, Success, Failure}
import ch07.LibUV.*, ch07.LibUVConstants.*

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
