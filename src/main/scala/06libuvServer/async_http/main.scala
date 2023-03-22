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
import string._
import stdlib._
object main {
  import LibUV._, LibUVConstants._
  import HTTP.RequestHandler

  type ClientState = CStruct3[Ptr[Byte],CSize,CSize]

  def main(args:Array[String]):Unit = {
    serve_http(8080, request =>
      HttpResponse(200, Map("Content-Length" -> "12"),"hello world\n"))
  }

  var router:RequestHandler = (_ => ???)

  def serve_http(port:Int, handler:RequestHandler):Unit = {
    println(s"about to serve on port ${port}")
    this.router = handler
    serve_tcp(c"0.0.0.0",port,0,4096,connectionCB)
  }

  val readCB = new ReadCB {
    def apply(client:TCPHandle, size:CSSize, buffer:Ptr[Buffer]):Unit = {
      if (size < 0) {
        shutdown(client)
      } else {
        try {
          val parsed_request = HTTP.parseRequest(buffer._1, size)
          val response = router(parsed_request)
          send_response(client,response)
          shutdown(client)    
        } catch {
          case e:Throwable => 
            println(s"error during parsing: ${e}")
            shutdown(client)
        }
      }
    }
  }

  def send_response(client:TCPHandle,response:HttpResponse):Unit = {
    val req = malloc(uv_req_size(UV_WRITE_REQ_T)).asInstanceOf[WriteReq]
    val resp_buffer = malloc(sizeof[Buffer]).asInstanceOf[Ptr[Buffer]]
    resp_buffer._1 = malloc(4096)
    resp_buffer._2 = 4096
    HTTP.make_response(response,resp_buffer)
    resp_buffer._2 = string.strlen(resp_buffer._1)
    !req = resp_buffer.asInstanceOf[Ptr[Byte]]
    check_error(uv_write(req,client,resp_buffer,1,writeCB), "uv_write")
  }

  val loop = uv_default_loop()

  def serve_tcp(address:CString, port:Int, flags:Int, backlog:Int, 
      callback:ConnectionCB):Unit = {
    val addr = stackalloc[Byte]
    val addr_convert = uv_ip4_addr(address, port, addr)
    println(s"uv_ip4_addr returned $addr_convert")
    val handle = malloc(uv_handle_size(UV_TCP_T)).asInstanceOf[TCPHandle]
    check_error(uv_tcp_init(loop, handle), "uv_tcp_init(server)")
    check_error(uv_tcp_bind(handle, addr, flags), "uv_tcp_bind")
    check_error(uv_listen(handle, backlog, callback), "uv_tcp_listen")
    uv_run(loop, UV_RUN_DEFAULT)
    ()
  }

val connectionCB = new ConnectionCB {
  def apply(handle:TCPHandle, status:Int):Unit = {
    println("received connection")
  
    // initialize the new client tcp handle and its state
    val client = malloc(uv_handle_size(UV_TCP_T)).asInstanceOf[TCPHandle]
    check_error(uv_tcp_init(loop,client), "uv_tcp_init(client)")
    var client_state_ptr = (!client).asInstanceOf[Ptr[ClientState]]
    client_state_ptr = initialize_client_state(client)
  
    // accept the incoming connection into the new handle
    check_error(uv_accept(handle,client), "uv_accept")
    // set up callbacks for incoming data
    check_error(uv_read_start(client,allocCB,readCB), "uv_read_start")
  }
}

def initialize_client_state(client:TCPHandle):Ptr[ClientState] = {
    val client_state_ptr = stdlib.malloc(sizeof[ClientState]).asInstanceOf[Ptr[ClientState]]
    stdio.printf(c"allocated data at %x; assigning into handle storage at %x\n", 
      client_state_ptr, client)
    val client_state_data = stdlib.malloc(4096)
    client_state_ptr._1 = client_state_data
    client_state_ptr._2 = 4096 // total
    client_state_ptr._3 = 0 // used
    !client = client_state_ptr.asInstanceOf[Ptr[Byte]]
    client_state_ptr
}

val allocCB = new AllocCB {
  def apply(client:TCPHandle, size:CSize, buffer:Ptr[Buffer]):Unit = {
    println("allocating 4096 bytes")
    val buf = stdlib.malloc(4096)
    buffer._1 = buf
    buffer._2 = 4096
  }
}

def append_data(state:Ptr[ClientState], size:CSSize, buffer:Ptr[Buffer]):Unit = {
    val copy_position = state._1 + state._3
    string.strncpy(copy_position, buffer._1, size)
    // be sure to update the length of the data since we have copied into it
    state._3 = state._3 + size
    stdio.printf(c"client %x: %d/%d bytes used\n", state, state._3, state._2)
}

def send_response(client:TCPHandle,state:Ptr[ClientState]):Unit = {
    val resp = malloc(uv_req_size(UV_WRITE_REQ_T)).asInstanceOf[WriteReq]
    val resp_buffer = malloc(sizeof[Buffer]).asInstanceOf[Ptr[Buffer]]
    resp_buffer._1 = make_response(state)
    resp_buffer._2 = string.strlen(resp_buffer._1)
    !resp = resp_buffer.asInstanceOf[Ptr[Byte]]
    check_error(uv_write(resp,client,resp_buffer,1,writeCB), "uv_write")
}

def make_response(state:Ptr[ClientState]):CString = {
  val response_format = c"received response:\n%s\n"
  val response_data = malloc(string.strlen(response_format) + state._3)
  stdio.sprintf(response_data, response_format, state._1)
  response_data
}

val writeCB = new WriteCB {
  def apply(writeReq:WriteReq, status:Int):Unit = {
    println("write completed")
    val resp_buffer = (!writeReq).asInstanceOf[Ptr[Buffer]]
    stdlib.free(resp_buffer._1)
    stdlib.free(resp_buffer.asInstanceOf[Ptr[Byte]])
    stdlib.free(writeReq.asInstanceOf[Ptr[Byte]])
  }
}

def shutdown(client:TCPHandle):Unit = {
  val shutdown_req = malloc(uv_req_size(UV_SHUTDOWN_REQ_T)).asInstanceOf[ShutdownReq]
  !shutdown_req = client.asInstanceOf[Ptr[Byte]]
  check_error(uv_shutdown(shutdown_req,client,shutdownCB), "uv_shutdown")
}

val shutdownCB = new ShutdownCB {
  def apply(shutdownReq:ShutdownReq, status:Int):Unit = {
    println("all pending writes complete, closing TCP connection")
    val client = (!shutdownReq).asInstanceOf[TCPHandle]
    check_error(uv_close(client,closeCB),"uv_close")
    stdlib.free(shutdownReq.asInstanceOf[Ptr[Byte]])
  }
}

val closeCB = new CloseCB {
  def apply(client:TCPHandle):Unit = {
    println("closed client connection")
    val client_state_ptr = (!client).asInstanceOf[Ptr[ClientState]]
    stdlib.free(client_state_ptr._1)
    stdlib.free(client_state_ptr.asInstanceOf[Ptr[Byte]])
    stdlib.free(client.asInstanceOf[Ptr[Byte]])
  }
}
}

@link("uv")
@extern
object LibUV {
  type TimerHandle = Ptr[Byte]

  type Loop = Ptr[Byte]
  type TimerCB = CFuncPtr1[TimerHandle,Unit]

  def uv_default_loop(): Loop = extern
  def uv_loop_size(): CSize = extern
  def uv_is_active(handle:Ptr[Byte]): Int = extern
  def uv_handle_size(h_type:Int): CSize = extern
  def uv_req_size(r_type:Int): CSize = extern

  def uv_timer_init(loop:Loop, handle:TimerHandle):Int = extern
  def uv_timer_start(handle:TimerHandle, cb:TimerCB, timeout:Long, repeat:Long):Int = extern
  def uv_timer_stop(handle:TimerHandle):Int = extern

  def uv_run(loop:Loop, runMode:Int): Int = extern

  def uv_strerror(err:Int): CString = extern
  def uv_err_name(err:Int): CString = extern

  type Buffer = CStruct2[Ptr[Byte],CSize]
  type TCPHandle = Ptr[Ptr[Byte]]
  type ConnectionCB = CFuncPtr2[TCPHandle, Int, Unit]
  type WriteReq = Ptr[Ptr[Byte]]
  type ShutdownReq = Ptr[Ptr[Byte]]

  def uv_tcp_init(loop:Loop, tcp_handle:TCPHandle):Int = extern
  def uv_tcp_bind(tcp_handle:TCPHandle, address:Ptr[Byte], flags:Int):Int = extern
  def uv_listen(stream_handle:TCPHandle, backlog:Int, uv_connection_cb:ConnectionCB):
   Int = extern
  def uv_accept(server:TCPHandle, client:TCPHandle): Int = extern
  def uv_read_start(client:TCPHandle, allocCB:AllocCB, readCB:ReadCB): Int = extern
  def uv_write(writeReq:WriteReq, client:TCPHandle, bufs: Ptr[Buffer], numBufs:
   Int, writeCB:WriteCB): Int = extern
  def uv_shutdown(shutdownReq:ShutdownReq, client:TCPHandle, shutdownCB:ShutdownCB):
   Int = extern
  def uv_close(handle:TCPHandle, closeCB: CloseCB): Int = extern

  def uv_ip4_addr(address:CString, port:Int, out_addr:Ptr[Byte]):Int = extern
  def uv_ip4_name(address:Ptr[Byte], s:CString, size:Int):Int = extern

  type AllocCB = CFuncPtr3[TCPHandle,CSize,Ptr[Buffer],Unit]
  type ReadCB = CFuncPtr3[TCPHandle,CSSize,Ptr[Buffer],Unit]
  type WriteCB = CFuncPtr2[WriteReq,Int,Unit]
  type ShutdownCB = CFuncPtr2[ShutdownReq,Int,Unit]
  type CloseCB = CFuncPtr1[TCPHandle,Unit]
}

object LibUVConstants {
  import LibUV._

  // uv_run_mode
  val UV_RUN_DEFAULT = 0
  val UV_RUN_ONCE = 1
  val UV_RUN_NOWAIT = 2

  // UV_HANDLE_T
  val UV_PIPE_T = 7
  val UV_POLL_T = 8
  val UV_PREPARE_T = 9
  val UV_PROCESS_T = 10
  val UV_TCP_T = 12
  val UV_TIMER_T = 13
  val UV_TTY_T = 14
  val UV_UDP_T = 15

  // UV_REQ_T
  val UV_WRITE_REQ_T = 3
  val UV_SHUTDOWN_REQ_T = 4

  val UV_READABLE = 1
  val UV_WRITABLE = 2
  val UV_DISCONNECT = 4
  val UV_PRIORITIZED = 8

  def check_error(v:Int, label:String):Int = {
      if (v == 0) {
        println(s"$label returned $v")
        v
      } else {
        val error = fromCString(uv_err_name(v))
        val message = fromCString(uv_strerror(v))
        println(s"$label returned $v: $error: $message")
        v
      }
  }
}