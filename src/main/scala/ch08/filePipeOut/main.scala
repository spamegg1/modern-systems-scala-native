package ch08
package filePipeOut

import scalanative.unsigned.{UnsignedRichLong, UnsignedRichInt}
import scalanative.unsafe.*
import scalanative.libc.*, stdlib.*, string.strncpy
import collection.mutable
import scala.util.{Try, Success, Failure}
import concurrent.{Future, ExecutionContext, Promise}

case class FileOutputPipe(fd: Int, serial: Int, async: Boolean)
    extends Pipe[String, Unit]:
  import ch07.LibUV.*, ch07.LibUVConstants.*

  var offset = 0L

  val writeCB = if async then FileOutputPipe.writeCB else null

  override def feed(input: String): Unit =
    val outputSize = input.size
    val req = stdlib.malloc(uv_req_size(UV_FS_REQ_T)).asInstanceOf[FSReq]

    val outputBuffer = malloc(sizeof[Buffer]).asInstanceOf[Ptr[Buffer]]
    outputBuffer._1 = malloc(outputSize.toUSize) // 0.5
    Zone:
      val outputString = toCString(input)
      strncpy(outputBuffer._1, outputString, outputSize.toUSize) // 0.5

    outputBuffer._2 = outputSize.toUSize // 0.5
    !req = outputBuffer.asInstanceOf[Ptr[Byte]]

    uv_fs_write(ch07.EventLoop.loop, req, fd, outputBuffer, 1, offset, writeCB)
    offset += outputSize

  override def done(): Unit =
    val req = stdlib.malloc(uv_req_size(UV_FS_REQ_T)).asInstanceOf[FSReq]
    uv_fs_close(ch07.EventLoop.loop, req, fd, null)
    FileOutputPipe.activeStreams -= serial

object FileOutputPipe:
  import ch07.LibUV.*, ch07.LibUVConstants.*
  import stdlib.*

  var activeStreams: mutable.Set[Int] = mutable.Set()
  var serial = 0

  def apply(path: CString, async: Boolean = true): FileOutputPipe =
    activeStreams += serial

    stdio.printf(c"opening %s for writing..\n", path)
    val fd = util.open(path, O_RDWR + O_CREAT, default_permissions)
    println(s"got back fd: $fd")

    val pipe = FileOutputPipe(fd, serial, async)
    serial += 1
    println(s"initialized $pipe")
    pipe

  val writeCB = CFuncPtr1.fromScalaFunction[FSReq, Unit]: (req: FSReq) =>
    println("write completed")
    val resp_buffer = (!req).asInstanceOf[Ptr[Buffer]]
    stdlib.free(resp_buffer._1)
    stdlib.free(resp_buffer.asInstanceOf[Ptr[Byte]])
    stdlib.free(req.asInstanceOf[Ptr[Byte]])

  // def onShutdown(shutdownReq: ShutdownReq, status: Int): Unit =
  //   val client = (!shutdownReq).cast[PipeHandle]
  //   uv_close(client,closeCB)
  //   stdlib.free(shutdownReq.cast[Ptr[Byte]])
  //
  // val shutdownCB = CFunctionPtr.fromFunction2(onShutdown)

  // def onClose(client: PipeHandle): Unit = stdlib.free(client.cast[Ptr[Byte]])
  // val closeCB = CFunctionPtr.fromFunction1(onClose)

object SyncPipe:
  import ch07.LibUV.*, ch07.LibUVConstants.*

  var activeStreams: mutable.Set[Int] = mutable.Set()
  var handlers = mutable.HashMap[Int, Pipe[String, String]]()
  var serial = 0

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

import ch07.LibUV.*, ch07.LibUVConstants.*
given ec: ExecutionContext = ch07.EventLoop

@main
def filePipeOut(args: String*): Unit =
  val p = FilePipe(c"./data.txt")
    .map(d => { println(s"consumed $d"); d })
    .addDestination(Tokenizer("\n"))
    .addDestination(Tokenizer(" "))
    .map(d => d + "\n")
    .addDestination(FileOutputPipe(c"./output.txt", false))

  println("running")
  uv_run(ch07.EventLoop.loop, UV_RUN_DEFAULT)

@extern
object util:
  def open(path: CString, flags: Int, mode: Int): Int = extern
