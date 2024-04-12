package ch08
package filePipe

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.*
import scalanative.libc.*
import collection.mutable
import scala.util.{Try, Success, Failure}
import concurrent.{Future, ExecutionContext, Promise}

object SyncPipe:
  import ch07.LibUV.*, ch07.LibUVConstants.*

  var activeStreams: mutable.Set[Int] = mutable.Set()
  var handlers = mutable.HashMap[Int, Pipe[String, String]]()
  var serial = 0

  def activeRequests: Int = activeStreams.size

  def stream(fd: Int): Pipe[String, String] =
    val handle = stdlib.malloc(uv_handle_size(UV_PIPE_T))
    uv_pipe_init(ch07.EventLoop.loop, handle, 0)
    val pipeData = handle.asInstanceOf[Ptr[Int]]
    !pipeData = serial
    activeStreams += serial
    val pipe = Pipe.source[String]
    handlers(serial) = pipe

    serial += 1
    uv_pipe_open(handle, fd)
    uv_read_start(handle, allocCB, readCB)
    pipe

  val allocCB = CFuncPtr3.fromScalaFunction[TCPHandle, CSize, Ptr[Buffer], Unit]:
    (client: PipeHandle, size: CSize, buffer: Ptr[Buffer]) =>
      val buf = stdlib.malloc(4096.toUSize) // 0.5
      buffer._1 = buf
      buffer._2 = 4096.toUSize // 0.5

  val readCB = CFuncPtr3.fromScalaFunction[TCPHandle, CSSize, Ptr[Buffer], Unit]:
    (handle: TCPHandle, size: CSSize, buffer: Ptr[Buffer]) =>
      val pipeData = handle.asInstanceOf[Ptr[Int]]
      val pipeId = !pipeData
      println(s"read $size bytes from pipe $pipeId")
      if size < 0 then
        println("size < 0, closing")
        activeStreams -= pipeId
        val pipeDestination = handlers(pipeId)
        pipeDestination.done()
        handlers.remove(pipeId)
      else
        val dataBuffer = stdlib.malloc(size.toUSize) // 0.5
        string.strncpy(dataBuffer, buffer._1, size.toUSize) // 0.5
        val data_string = fromCString(dataBuffer)
        stdlib.free(dataBuffer)
        val pipeDestination = handlers(pipeId)
        pipeDestination.feed(data_string.trim())

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
