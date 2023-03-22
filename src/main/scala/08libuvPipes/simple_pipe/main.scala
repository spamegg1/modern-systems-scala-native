/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
import scala.scalanative.unsafe._
import scala.scalanative.libc.stdlib
import scala.scalanative.libc.string
import collection.mutable
import scala.util.{Try,Success,Failure}

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
  // ...
  def map[V](g:U => V):Pipe[U,V] = {
    val destination = SyncPipe(g)
    handlers += destination
    destination
  }
}

case class SyncPipe[T,U](f:T => U) extends Pipe[T,U] {
  def feed(input:T):Unit = {
    val output = f(input)
    for (h <- handlers) {
      h.feed(output)
    }
  }
}

object SyncPipe {
  import LibUV._, LibUVConstants._

  var active_streams:mutable.Set[Int] = mutable.Set()
  var handlers = mutable.HashMap[Int,SyncPipe[String,String]]()
  var serial = 0

  def apply(fd:Int):SyncPipe[String,String] = {
    val handle = stdlib.malloc(uv_handle_size(UV_PIPE_T))
    uv_pipe_init(EventLoop.loop,handle,0)
    val pipe_data = handle.asInstanceOf[Ptr[Int]]
    !pipe_data = serial
    active_streams += serial
    val pipe = SyncPipe[String,String]{ s => s }
    handlers(serial) = pipe

    serial += 1
    uv_pipe_open(handle,fd)
    uv_read_start(handle,SyncPipe.allocCB,SyncPipe.readCB)
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
  def main(args:Array[String]):Unit = {
    println("hello!")
    val p = SyncPipe(0)
    val q = p.map { d =>
      println(s"consumed $d")
      d
    }.map { d =>
      val parsed = Try {
        d.toInt
      }
      println(s"parsed: $parsed")
      parsed
    }.map {
      case Success(i) => println(s"saw number $i")
      case Failure(f) => println(s"error: $f")
    }
    uv_run(EventLoop.loop,UV_RUN_DEFAULT)
    println("done")
  }
}
