/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.libc._

// import scalanative.native._
import stdio._, string._, stdlib._
import scala.scalanative.posix.unistd._
import scala.scalanative.posix.sys.socket._
import scala.scalanative.posix.netinet.in._
import scala.scalanative.posix.arpa.inet._
import scala.collection.mutable

case class HttpRequest(
  method:String,
  uri:String,
  headers:collection.Map[String, String],
  body:String)

case class HttpResponse(
  code:Int,
  headers:collection.Map[String, String],
  body:String)

object Parsing {
  def parseHeaderLine(line:CString):(String, String) = {
    val key_buffer = stackalloc[Byte](64)
    val value_buffer = stackalloc[Byte](64)
    val scan_result = stdio.sscanf(line, c"%s %s\n", key_buffer, value_buffer)
    if (scan_result < 2) {
      throw new Exception("bad header line")
    } else {
      val key_string = fromCString(key_buffer)
      val value_string = fromCString(value_buffer)
      return (key_string, value_string)
    }
  }

  def parseRequestLine(line:CString):(String, String) = {
    val method_buffer = stackalloc[Byte](16)
    val url_buffer = stackalloc[Byte](1024)
    val protocol_buffer = stackalloc[Byte](32)
    val scan_result = stdio.sscanf(line, c"%s %s %s\n", method_buffer, url_buffer, protocol_buffer)
    if (scan_result <3 ) {
      throw new Exception("bad request line")
    } else {
      val method = fromCString(method_buffer)
      val url = fromCString(url_buffer)
      return (method,url)
    }
  }

  def parse_request(conn:Int):Option[HttpRequest] = {
    val socket_fd = util.fdopen(conn, c"r")
    val line_buffer = stdlib.malloc(4096) // fix
    var read_result = stdio.fgets(line_buffer, 4096, socket_fd)
    val (method, url) = parseRequestLine(line_buffer)
    stdio.printf(c"read request line: %s", line_buffer)
    println( s"${(method,url)}")
    var headers = mutable.Map[String, String]()
    read_result = stdio.fgets(line_buffer, 4096, socket_fd)
    var line_length = string.strlen(line_buffer)

    while (line_length > 2) {
      val (k,v) = parseHeaderLine(line_buffer)
      headers(k) = v
      read_result = stdio.fgets(line_buffer, 4096, socket_fd)
      line_length = string.strlen(line_buffer)
    }

    return Some(HttpRequest(method, url, headers, ""))
  }

  def write_response(conn:Int,resp:HttpResponse):Unit = {
    val socket_fd = util.fdopen(conn, c"r+")
    Zone { implicit z =>
      stdio.fprintf(socket_fd, c"%s %s %s\r\n", c"HTTP/1.1", c"200", c"OK")
      for ((k,v) <- resp.headers) {
        stdio.fprintf(socket_fd, c"%s: %s\r\n", toCString(k), toCString(v))
      }
      stdio.fprintf(socket_fd, c"\r\n")
      stdio.fprintf(socket_fd, toCString(resp.body))
    }
    fclose(socket_fd)
    ()
  }
}

object Main {
  def main(args:Array[String]):Unit = {
    serve(8082.toUShort)
  }

def serve(port:UShort): Unit = {
    // Allocate and initialize the server address
    val addr_size = sizeof[sockaddr_in]
    val server_address = malloc(addr_size).asInstanceOf[Ptr[sockaddr_in]]
    server_address._1 = AF_INET.toUShort  // IP Socket
    server_address._2 = htons(port)       // port
    server_address._3._1 = htonl(INADDR_ANY)     // bind to 0.0.0.0

    // Bind and listen on a socket
    val sock_fd = socket(AF_INET, SOCK_STREAM, 0)
    val server_sockaddr = server_address.asInstanceOf[Ptr[sockaddr]]
    val bind_result = bind(sock_fd, server_sockaddr, addr_size.toUInt)
    println(s"bind returned $bind_result")
    val listen_result = listen(sock_fd, 128)
    println(s"listen returned $listen_result")

    val incoming = malloc(sizeof[sockaddr_in]).asInstanceOf[Ptr[sockaddr]]
    val inc_sz = malloc(sizeof[UInt]).asInstanceOf[Ptr[UInt]]
    !inc_sz = sizeof[sockaddr_in].toUInt
    println(s"accepting connections on port $port")

    // Main accept() loop
    while (true) {
        println(s"accepting")
        val conn_fd = accept(sock_fd, incoming, inc_sz)
        println(s"accept returned fd $conn_fd")
        if (conn_fd <= 0) {
          val err = errno.errno
          val errString = string.strerror(err)
          stdio.printf(c"errno: %d %s\n", err, errString)
        }
        // we will replace handle_connection with fork_and_handle shortly
        handle_connection(conn_fd)
        close(conn_fd)
    }
    close(sock_fd)
}

import Parsing._
def handle_connection(conn_socket:Int, max_size:Int = 1024): Unit = {
  while(true) {
    parse_request(conn_socket) match {
      case Some(request) =>
        val response = handleRequest(request)
        write_response(conn_socket, response)
        return
      case None =>
        return
    }
  }
}

def handleRequest(request:HttpRequest): HttpResponse = {
  val headers = Map("Content-type" -> "text/html")
  val body = s"received ${request.toString}\n"
  return HttpResponse(200, headers, body)
}

}

@extern
object util {
  def fdopen(fd:Int, mode:CString):Ptr[FILE] = extern
}
