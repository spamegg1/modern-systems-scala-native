package ch08
package filePipe

import scalanative.unsigned.{UnsignedRichLong, UnsignedRichInt}
import scalanative.unsafe.*
import scalanative.libc.*, stdlib.*, string.strncpy
import collection.mutable
import scala.util.{Try, Success, Failure}
import concurrent.{Future, ExecutionContext, Promise}
import ch07.LibUV.*, ch07.LibUVConstants.*

case class FileOutputPipe(fd: Int, serial: Int, async: Boolean)
    extends Pipe[String, Unit]:

  var offset = 0L

  val writeCB = if async then FileOutputPipe.writeCB else null

  override def feed(input: String): Unit =
    val outputSize = input.size
    val req = stdlib.malloc(uv_req_size(UV_FS_REQ_T)).asInstanceOf[FSReq] // writeCB frees

    val outputBuffer = malloc(sizeof[Buffer]).asInstanceOf[Ptr[Buffer]] // freed where?
    outputBuffer._1 = malloc(outputSize)
    Zone:
      val outputString = toCString(input)
      strncpy(outputBuffer._1, outputString, outputSize.toUSize) // 0.5

    outputBuffer._2 = outputSize.toUSize // 0.5
    !req = outputBuffer.asInstanceOf[Ptr[Byte]]

    // presumably, this frees outputBuffer
    uv_fs_write(ch07.EventLoop.loop, req, fd, outputBuffer, 1, offset, writeCB)
    offset += outputSize

  override def done(): Unit =
    val req = stdlib.malloc(uv_req_size(UV_FS_REQ_T)).asInstanceOf[FSReq] // freed where?
    uv_fs_close(ch07.EventLoop.loop, req, fd, null) // here?
    FileOutputPipe.activeStreams -= serial

object FileOutputPipe:
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
    val respBuffer = (!req).asInstanceOf[Ptr[Buffer]]
    stdlib.free(respBuffer._1)
    stdlib.free(respBuffer.asInstanceOf[Ptr[Byte]])
    stdlib.free(req.asInstanceOf[Ptr[Byte]]) // it's freed here!

  // def onShutdown(shutdownReq: ShutdownReq, status: Int): Unit =
  //   val client = (!shutdownReq).cast[PipeHandle]
  //   uv_close(client,closeCB)
  //   stdlib.free(shutdownReq.cast[Ptr[Byte]])
  //
  // val shutdownCB = CFunctionPtr.fromFunction2(onShutdown)

  // def onClose(client: PipeHandle): Unit = stdlib.free(client.cast[Ptr[Byte]])
  // val closeCB = CFunctionPtr.fromFunction1(onClose)

@main
def run: Unit =
  given ec: ExecutionContext = ch07.EventLoop

  val p = FilePipe(c"./data.txt")
    .map: d =>
      println(s"consumed $d")
      d
    .addDestination(Tokenizer("\n"))
    .addDestination(Tokenizer(" "))
    .map(d => d + "\n")
    .addDestination(FileOutputPipe(c"./output.txt", false))

  println("running")
  uv_run(ch07.EventLoop.loop, UV_RUN_DEFAULT)
