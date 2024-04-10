package ch08.simplePipe

import scalanative.unsigned.{UnsignedRichLong, UnsignedRichInt}
import scalanative.unsafe.*
import scalanative.libc.stdlib
import scalanative.libc.string
import collection.mutable
import scala.util.{Try, Success, Failure}

trait Pipe[T, U]:
  val handlers = mutable.Set[Pipe[U, ?]]()

  def feed(input: T): Unit
  def done(): Unit = for h <- handlers do h.done()

  def addDestination[V](dest: Pipe[U, V]): Pipe[U, V] =
    handlers += dest
    dest

  // ...
  def map[V](g: U => V): Pipe[U, V] =
    val destination = SyncPipe(g)
    handlers += destination
    destination

case class SyncPipe[T, U](f: T => U) extends Pipe[T, U]:
  def feed(input: T): Unit =
    val output = f(input)
    for h <- handlers do h.feed(output)

object SyncPipe:
  import ch07.LibUV.*, ch07.LibUVConstants.*

  var activeStreams: mutable.Set[Int] = mutable.Set()
  var handlers = mutable.HashMap[Int, SyncPipe[String, String]]()
  var serial = 0

  def apply(fd: Int): SyncPipe[String, String] =
    val handle = stdlib.malloc(uv_handle_size(UV_PIPE_T))
    uv_pipe_init(ch07.EventLoop.loop, handle, 0)
    val pipeData = handle.asInstanceOf[Ptr[Int]]
    !pipeData = serial
    activeStreams += serial
    val pipe = SyncPipe[String, String] { s => s }
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

import ch07.LibUV.*, ch07.LibUVConstants.*

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
