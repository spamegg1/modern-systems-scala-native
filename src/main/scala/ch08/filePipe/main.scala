package ch08
package filePipe

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.*
import scalanative.libc.*
import collection.mutable
import scala.util.{Try, Success, Failure}
import concurrent.{Future, ExecutionContext, Promise}

import ch07.LibUV.*, ch07.LibUVConstants.*
given ec: ExecutionContext = ch07.EventLoop

@main
def filePipeMain(args: String*): Unit =
  println("hello!")
  val p = FilePipe.apply(c"./data.text")
  println("ok")
  var buffer = ""
  val done = p
    .map(d => { println(s"consumed $d"); d })
    .addDestination(Tokenizer("\n"))
    .addDestination(Tokenizer(" "))
    .map(word => println(s"word: $word"))
    .onComplete
    .map(_ => println("stream completed!"))

  println("running")
  uv_run(ch07.EventLoop.loop, UV_RUN_DEFAULT)

@extern
object util:
  def open(path: CString, flags: Int, mode: Int): Int = extern
