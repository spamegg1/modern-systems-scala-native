/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
import scala.scalanative.unsafe._
import scala.scalanative.libc._
import collection.mutable
import scala.util.{Try,Success,Failure}
import scala.concurrent.{Future,ExecutionContext}
import scala.concurrent.{Promise}

trait Pipe[T,U] {
  val handlers = mutable.Set[Pipe[U,_]]()

  def feed(input:T):Unit
  def done():Unit = {
    for (h <- handlers) {
      h.done()
    }
  }

  def addDestination[V](dest:Pipe[U,V]):Pipe[U,V] = {
    handlers += dest
    dest
  }

  case class SyncPipe[T,U](f:T => U) extends Pipe[T,U] {
    override def feed(input:T):Unit = {
      val output = f(input)
      for (h <- handlers) {
        h.feed(output)
      }
    }
  }

  def map[V](g:U => V):Pipe[U,V] = {
    addDestination(SyncPipe(g))
  }

  case class ConcatPipe[T,U](f:T => Seq[U]) extends Pipe[T,U] {
    override def feed(input:T):Unit = {
      val output = f(input)
      for (h <- handlers;
           o <- output) {
        h.feed(o)
      }
    }
  }

  def mapConcat[V](g:U => Seq[V]):Pipe[U,V] = {
    addDestination(ConcatPipe(g))
  }

  case class OptionPipe[T,U](f:T => Option[U]) extends Pipe[T,U] {
    override def feed(input:T):Unit = {
      val output = f(input)
      for (h <- handlers;
           o <- output) {
        h.feed(o)
      }
    }
  }

  def mapOption[V](g:U => Option[V]):Pipe[U,V] = {
    addDestination(OptionPipe(g))
  }

  case class AsyncPipe[T,U](f:T => Future[U])(implicit ec:ExecutionContext) extends Pipe[T,U] {
    override def feed(input:T):Unit = {
      f(input).map { o =>
        for (h <- handlers) {
          h.feed(o)
        }
      }
    }
  }

  def mapAsync[V](g:U => Future[V])(implicit ec:ExecutionContext):Pipe[U,V] = {
    addDestination(AsyncPipe(g))
  }

  def onComplete(implicit ec:ExecutionContext):Future[Unit] = {
    val sink = OnComplete[U]()
    addDestination(sink)
    return sink.promise.future
  }
}

case class OnComplete[T]()(implicit ec:ExecutionContext) extends Pipe[T,Unit] {
  val promise = Promise[Unit]()
  override def feed(input:T) = {}

  override def done() = {
    println("done, completing promise")
    promise.success(())
  }
}

case class CounterSink[T]() extends Pipe[T,Nothing] {
  var counter = 0
  override def feed(input:T) = {
    counter += 1
  }
}

case class FileOutputPipe(fd:Int, serial:Int, async:Boolean) 
  extends Pipe[String,Unit] {
  import LibUV._, LibUVConstants._
  import stdlib._, string._
  var offset = 0L

  val writeCB = if (async) { FileOutputPipe.writeCB } else null

  override def feed(input:String):Unit = {
    val output_size = input.size
    val req = stdlib.malloc(uv_req_size(UV_FS_REQ_T)).asInstanceOf[FSReq]

    val output_buffer = malloc(sizeof[Buffer]).asInstanceOf[Ptr[Buffer]]
    output_buffer._1 = malloc(output_size)
    Zone { implicit z =>
      val output_string = toCString(input)
      strncpy(output_buffer._1, output_string, output_size)
    }
    output_buffer._2 = output_size
    !req = output_buffer.asInstanceOf[Ptr[Byte]]

    uv_fs_write(EventLoop.loop,req,fd,output_buffer,1,offset,writeCB)
    offset += output_size
  }

  override def done():Unit = {
    val req = stdlib.malloc(uv_req_size(UV_FS_REQ_T)).asInstanceOf[FSReq]
    uv_fs_close(EventLoop.loop,req,fd,null)
    FileOutputPipe.active_streams -= serial
  }
}

object FileOutputPipe {
  import LibUV._, LibUVConstants._
  import stdlib._

  var active_streams:mutable.Set[Int] = mutable.Set()
  var serial = 0

  def apply(path:CString, async:Boolean = true):FileOutputPipe = {
    active_streams += serial

    stdio.printf(c"opening %s for writing..\n", path)
    val fd = util.open(path,O_RDWR + O_CREAT,default_permissions)
    println(s"got back fd: $fd")


    val pipe = FileOutputPipe(fd,serial,async)
    serial += 1
    println(s"initialized $pipe")
    pipe
  }

  val writeCB = new FSCB {
    def apply(req:FSReq):Unit = {
      println("write completed")
      val resp_buffer = (!req).asInstanceOf[Ptr[Buffer]]
      stdlib.free(resp_buffer._1)
      stdlib.free(resp_buffer.asInstanceOf[Ptr[Byte]])
      stdlib.free(req.asInstanceOf[Ptr[Byte]])
    }
  }

  // def on_shutdown(shutdownReq:ShutdownReq, status:Int):Unit = {
  //   val client = (!shutdownReq).cast[PipeHandle]
  //   uv_close(client,closeCB)
  //   stdlib.free(shutdownReq.cast[Ptr[Byte]])
  // }
  // val shutdownCB = CFunctionPtr.fromFunction2(on_shutdown)

  // def on_close(client:PipeHandle):Unit = {
  //   stdlib.free(client.cast[Ptr[Byte]])
  // }
  // val closeCB = CFunctionPtr.fromFunction1(on_close)
}

case class Tokenizer(separator:String) extends Pipe[String,String] {
  var buffer = ""

  def scan(input:String):Seq[String] = {
      println(s"scanning: '$input'")
      buffer = buffer + input
      var o:Seq[String] = Seq()
      while (buffer.contains(separator)) {
        val space_position = buffer.indexOf(separator)
        val word = buffer.substring(0,space_position)

        o = o :+ word

        buffer = buffer.substring(space_position + 1)
      }
      o

  }
  override def feed(input:String):Unit = {
    for (h    <- handlers;
         word <- scan(input)) {
           h.feed(word)
    }
  }

  override def done():Unit = {
    println(s"done!  current buffer: $buffer")
    for (h <- handlers) {
           h.feed(buffer)
           h.done()
    }
  }
}

case class FoldPipe[I,O](init:O)(f:(O,I) => O) extends Pipe[I,O] {
  var accum = init

  override def feed(input:I):Unit = {
    accum = f(accum,input)
    for (h <- handlers) {
      h.feed(accum)
    }
  }

  override def done():Unit = {
    for (h <- handlers) {
      h.done()
    }
  }
}

object Pipe {
  case class PipeSource[I]() extends Pipe[I,I] {
    override def feed(input:I):Unit = {
      for (h <- handlers) {
        h.feed(input)
      }
    }
  }
  def source[I]:Pipe[I,I] = {
    PipeSource[I]()
  }
}

case class FilePipe(serial:Long) extends Pipe[String,String] {
  override def feed(input:String):Unit = {
    for (h <- handlers) {
      h.feed(input)
    }
  }
}

object FilePipe {
  import LibUV._, LibUVConstants._
  type FilePipeState = CStruct3[Int,Ptr[Buffer],Long] // fd, buffer, offset

  var active_streams:mutable.Set[Int] = mutable.Set()
  var handlers = mutable.HashMap[Int,Pipe[String,String]]()
  var serial = 0


def apply(path:CString):Pipe[String,String] = {
  val req = stdlib.malloc(uv_req_size(UV_FS_REQ_T)).asInstanceOf[FSReq]
  println("opening file")
  val fd = util.open(path,0,0)
  stdio.printf(c"open file at %s returned %d\n", path, fd)

  val state = stdlib.malloc(sizeof[FilePipeState])
    .asInstanceOf[Ptr[FilePipeState]]
  val buf = stdlib.malloc(sizeof[Buffer]).asInstanceOf[Ptr[Buffer]]
  buf._1 = stdlib.malloc(4096)
  buf._2 = 4095
  state._1 = fd
  state._2 = buf
  state._3 = 0L
  !req = state.asInstanceOf[Ptr[Byte]]

  println("about to read")
  uv_fs_read(EventLoop.loop,req,fd,buf,1,-1,readCB)
  println("read started")
  val pipe = Pipe.source[String]
  handlers(fd) = pipe
  println("about to return")
  active_streams += fd
  pipe
}

val readCB:FSCB = new FSCB {
  def apply(req:FSReq):Unit = {
    println("read callback fired!")
    val res = uv_fs_get_result(req)
    println(s"got result: $res")
    val state_ptr = (!req).asInstanceOf[Ptr[FilePipeState]]
    println("inspecting state")
    val fd = state_ptr._1
    val buf = state_ptr._2
    val offset = state_ptr._3
    printf("state: fd %d, offset %d\n", fd, offset.toInt)

    if (res > 0) {
      println("producing string")
      (buf._1)(res) = 0
      val output = fromCString(buf._1)
      val pipe = handlers(fd)
      pipe.feed(output)
      println("continuing")
      state_ptr._3 = state_ptr._3 + res
      uv_fs_read(EventLoop.loop,req,fd,state_ptr._2,1,state_ptr._3,readCB)
    } else if (res == 0) {
      println("done")
      val pipe = handlers(fd)
      pipe.done()
      active_streams -= fd
    } else {
      println("error")
      active_streams -= fd
    }
  }
}
}

object SyncPipe {
  import LibUV._, LibUVConstants._

  var active_streams:mutable.Set[Int] = mutable.Set()
  var handlers = mutable.HashMap[Int,Pipe[String,String]]()
  var serial = 0

  def stream(fd:Int):Pipe[String,String] = {
    val handle = stdlib.malloc(uv_handle_size(UV_PIPE_T))
    uv_pipe_init(EventLoop.loop,handle,0)
    val pipe_data = handle.asInstanceOf[Ptr[Int]]
    !pipe_data = serial
    active_streams += serial
    val pipe = Pipe.source[String]
    handlers(serial) = pipe

    serial += 1
    uv_pipe_open(handle,fd)
    uv_read_start(handle,allocCB,readCB)
    pipe
  }

  val allocCB = new AllocCB {
    def apply(client:PipeHandle, size:CSize, buffer:Ptr[Buffer]):Unit = {
      val buf = stdlib.malloc(4096)
      buffer._1 = buf
      buffer._2 = 4096
    }
  }

  val readCB = new ReadCB {
    def apply(handle:PipeHandle,size:CSize,buffer:Ptr[Buffer]):Unit = {
      val pipe_data = handle.asInstanceOf[Ptr[Int]]
      val pipe_id = !pipe_data
      println(s"read $size bytes from pipe $pipe_id")
      if (size < 0) {
        println("size < 0, closing")
        active_streams -= pipe_id
        val pipe_destination = handlers(pipe_id)
        pipe_destination.done()
        handlers.remove(pipe_id)
      } else {
        val data_buffer = stdlib.malloc(size + 1)
        string.strncpy(data_buffer, buffer._1, size + 1)
        val data_string = fromCString(data_buffer)
        stdlib.free(data_buffer)
        val pipe_destination = handlers(pipe_id)
        pipe_destination.feed(data_string.trim())
      }
    }
  }
}

object Main {
  import LibUV._, LibUVConstants._
  implicit val ec = EventLoop
  def main(args:Array[String]):Unit = {
    val p = FilePipe(c"./data.txt")
    .map { d =>
      println(s"consumed $d")
      d
    }.addDestination(Tokenizer("\n"))
    .addDestination(Tokenizer(" "))
    .map { d => d + "\n" }
    .addDestination(FileOutputPipe(c"./output.txt", false))
    println("running")
    uv_run(EventLoop.loop,UV_RUN_DEFAULT)
  }
}

@extern
object util {
  def open(path:CString, flags:Int, mode:Int):Int = extern
}
