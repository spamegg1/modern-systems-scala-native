package ch08
package syncPipe

import scalanative.unsigned.{UnsignedRichLong, UnsignedRichInt}
import scalanative.unsafe.*
import scalanative.libc.*, stdlib.*, string.strncpy
import collection.mutable
import scala.util.{Try, Success, Failure}
import concurrent.{Future, ExecutionContext, Promise}

case class SyncPipe[T, U](f: T => U) extends Pipe[T, U]:
  def feed(input: T): Unit = // implements the unimplemented method in Pipe
    val output = f(input)
    for h <- handlers do h.feed(output)

object SyncPipe:
  import ch07.LibUV.*, ch07.LibUVConstants.*

  var activeStreams: mutable.Set[Int] = mutable.Set()
  var handlers = mutable.HashMap[Int, Pipe[String, String]]()
  var serial = 0

  def activeRequests: Int = activeStreams.size

  def apply(fd: Int): SyncPipe[String, String] = // book different than code, this is book
    val handle = stdlib.malloc(uv_handle_size(UV_PIPE_T))
    uv_pipe_init(ch07.EventLoop.loop, handle, 0)

    val pipeData = handle.asInstanceOf[Ptr[Int]]
    !pipeData = serial
    activeStreams += serial

    val pipe = SyncPipe[String, String](identity) // book diffrent than code, this is book
    handlers(serial) = pipe

    serial += 1
    uv_pipe_open(handle, fd)
    uv_read_start(handle, allocCB, readCB)

    pipe

  val allocCB: AllocCB = CFuncPtr3.fromScalaFunction:
    (client: PipeHandle, size: CSize, buffer: Ptr[Buffer]) =>
      val buf = stdlib.malloc(4096) // malloc can take Int now! No need for .toUSize
      buffer._1 = buf // Buffer = CStruct2[Ptr[Byte], CSize]
      buffer._2 = 4096.toUSize // 0.5

  val readCB: ReadCB = CFuncPtr3.fromScalaFunction:
    (handle: TCPHandle, size: CSSize, buffer: Ptr[Buffer]) => // book has CSize
      val pipeData = handle.asInstanceOf[Ptr[Int]]
      val pipeId = !pipeData
      println(s"read $size bytes from pipe $pipeId")

      if size < 0 then // need CSSize = Size = signed, to compare < 0
        println("size < 0, closing")
        activeStreams -= pipeId
        val pipeDestination = handlers(pipeId) // not in the book
        pipeDestination.done() // not in the book
        handlers.remove(pipeId)
      else
        val stringSize = size.toUSize + 1.toUSize

        val dataBuffer = stdlib.malloc(stringSize)
        string.strncpy(dataBuffer, buffer._1, stringSize)

        val dataString = fromCString(dataBuffer)
        stdlib.free(dataBuffer)

        val pipeDestination = handlers(pipeId)
        pipeDestination.feed(dataString.trim())
