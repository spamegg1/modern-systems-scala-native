package ch08
package simplePipe

import scalanative.unsigned.{UnsignedRichLong, UnsignedRichInt}
import scalanative.unsafe.*
import scalanative.libc.stdlib
import scalanative.libc.string
import collection.mutable
import scala.util.{Try, Success, Failure}
import ch07.LibUV.*, ch07.LibUVConstants.*

object SyncPipe:
  var activeStreams: mutable.Set[Int] = mutable.Set()
  var handlers = mutable.HashMap[Int, SyncPipe[String, String]]()
  var serial = 0

  def apply(fd: Int): SyncPipe[String, String] =
    val handle = stdlib.malloc(uv_handle_size(UV_PIPE_T))
    uv_pipe_init(ch07.EventLoop.loop, handle, 0)
    val pipeData = handle.asInstanceOf[Ptr[Int]]
    !pipeData = serial
    activeStreams += serial
    val pipe = new SyncPipe[String, String](s => s)
    handlers(serial) = pipe

    serial += 1
    uv_pipe_open(handle, fd)
    uv_read_start(handle, SyncPipe.allocCB, SyncPipe.readCB)
    pipe

  val allocCB = CFuncPtr3.fromScalaFunction[PipeHandle, CSize, Ptr[Buffer], Unit]:
    (client: PipeHandle, size: CSize, buffer: Ptr[Buffer]) =>
      val buf = stdlib.malloc(4096.toUSize) // 0.5
      buffer._1 = buf
      buffer._2 = 4096.toUSize // 0.5

  val readCB: ReadCB = CFuncPtr3.fromScalaFunction[TCPHandle, CSSize, Ptr[Buffer], Unit]:
    (handle: TCPHandle, size: CSSize, buffer: Ptr[Buffer]) =>
      val pipeData = handle.asInstanceOf[Ptr[Int]]
      val pipeId = !pipeData
      println(s"read $size bytes from pipe $pipeId")
      if size < 0 then
        println("size < 0, closing")
        activeStreams -= pipeId
        handlers.remove(pipeId)
      else
        val dataBuffer = stdlib.malloc(size.toUSize) // removed +1 // 0.5
        string.strncpy(dataBuffer, buffer._1, size.toUSize) // removed +1 // 0.5
        val dataString = fromCString(dataBuffer)
        stdlib.free(dataBuffer)
        val pipeDestination = handlers(pipeId)
        pipeDestination.feed(dataString.trim())

@main
def simplePipe(args: String*): Unit =
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
