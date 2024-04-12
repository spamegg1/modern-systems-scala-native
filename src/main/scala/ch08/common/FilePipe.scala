package ch08

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.*
import scalanative.libc.{stdio, stdlib}
import collection.mutable

case class FilePipe(serial: Long) extends Pipe[String, String]:
  override def feed(input: String): Unit = for h <- handlers do h.feed(input)

object FilePipe:
  import ch07.LibUV.*, ch07.LibUVConstants.*

  type FilePipeState = CStruct3[Int, Ptr[Buffer], Long] // fd, buffer, offset

  var activeStreams: mutable.Set[Int] = mutable.Set()
  var handlers = mutable.HashMap[Int, Pipe[String, String]]()
  var serial = 0

  def apply(path: CString): Pipe[String, String] =
    val req = stdlib.malloc(uv_req_size(UV_FS_REQ_T)).asInstanceOf[FSReq]
    checkError(uv_fs_open(ch07.EventLoop.loop, req, path, 0, 0, null), "uv_fs_open")
    println("opening file")
    val fd = util.open(path, 0, 0)
    stdio.printf(c"open file at %s returned %d\n", path, fd)

    // val fd2 = util.open(c"./test2",0,0)
    // stdio.printf(c"open file at %s returned %d\n", c"./test2", fd2)
    val state = stdlib.malloc(sizeof[FilePipeState]).asInstanceOf[Ptr[FilePipeState]]
    val buf = stdlib.malloc(sizeof[Buffer]).asInstanceOf[Ptr[Buffer]]
    buf._1 = stdlib.malloc(4096) // 0.5
    buf._2 = 4095.toUSize // 0.5
    state._1 = fd
    state._2 = buf
    state._3 = 0L
    !req = state.asInstanceOf[Ptr[Byte]]

    println("about to read")
    uv_fs_read(ch07.EventLoop.loop, req, fd, buf, 1, -1, readCB)
    println("read started")
    val pipe = Pipe.source[String]
    handlers(fd) = pipe
    println("about to return")
    activeStreams += fd
    pipe

  val readCB: FSCB = CFuncPtr1.fromScalaFunction[FSReq, Unit]: (req: FSReq) =>
    println("read callback fired!")
    val res = uv_fs_get_result(req)
    println(s"got result: $res")
    val state_ptr = (!req).asInstanceOf[Ptr[FilePipeState]]
    println("inspecting state")
    val fd = state_ptr._1
    val buf = state_ptr._2
    val offset = state_ptr._3
    printf("state: fd %d, offset %d\n", fd, offset.toInt)

    if res > 0 then
      println("producing string")
      (buf._1)(res) = 0.toByte // null termination?
      val output = fromCString(buf._1)
      val pipe = handlers(fd)
      pipe.feed(output)
      println("continuing")
      state_ptr._3 = state_ptr._3 + res
      uv_fs_read(ch07.EventLoop.loop, req, fd, state_ptr._2, 1, state_ptr._3, readCB)
    else if res == 0 then
      println("done")
      val pipe = handlers(fd)
      pipe.done()
      activeStreams -= fd
    else
      println("error")
      activeStreams -= fd

@extern
object util:
  def open(path: CString, flags: Int, mode: Int): Int = extern
