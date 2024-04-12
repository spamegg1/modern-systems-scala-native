package ch08

import scalanative.unsigned.{UnsignedRichLong, UnsignedRichInt}
import scalanative.unsafe.*
import scalanative.libc.*, stdlib.*, string.strncpy
import collection.mutable
import scala.util.{Try, Success, Failure}
import concurrent.{Future, ExecutionContext, Promise}

case class SyncPipe[T, U](f: T => U) extends Pipe[T, U]:
  override def feed(input: T): Unit =
    val output = f(input)
    for h <- handlers do h.feed(output)

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

  val readCB: ReadCB = CFuncPtr3.fromScalaFunction[TCPHandle, CSSize, Ptr[Buffer], Unit]:
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
        val dataBuffer = stdlib.malloc(size.toUSize) // removed +1 // 0.5
        string.strncpy(dataBuffer, buffer._1, size.toUSize) // removed +1 // 0.5
        val dataString = fromCString(dataBuffer)
        stdlib.free(dataBuffer)
        val pipeDestination = handlers(pipeId)
        pipeDestination.feed(dataString.trim())
