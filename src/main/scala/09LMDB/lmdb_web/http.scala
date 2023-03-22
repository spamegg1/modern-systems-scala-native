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
import stdio._
import stdlib._
import string._
import scala.collection.mutable

case class HeaderLine(key:CString, value:CString, key_len:UShort, value_len:UShort)

case class HttpRequest(
  method:String,
  uri:String,
  headers:collection.Map[String, String],
  body:String)

case class HttpResponse(
  code:Int,
  headers:collection.Map[String, String],
  body:String)

object HTTP {
  import LibUV._, LibUVConstants._
  type RequestHandler = Function1[HttpRequest,HttpResponse]
  val HEADER_COMPLETE_NO_BODY = 0
  val HEADERS_INCOMPLETE = -1

  val MAX_URI_SIZE = 2048
  val MAX_METHOD_SIZE = 8

  val method_buffer = malloc(16)
  val uri_buffer = malloc(4096)

  def scan_request_line(line:CString):(String,String,Int) = {
    val line_len = stackalloc[Int]
    val scan_result = stdio.sscanf(line, c"%s %s %*s\r\n%n", method_buffer, uri_buffer, line_len)
    if (scan_result == 2) {
      (fromCString(method_buffer), fromCString(uri_buffer), !line_len)
    } else {
      throw new Exception("bad request line")
    }
  }

  def scan_header_line(line:CString, out_map:mutable.Map[String,String],key_end:Ptr[Int], 
                       value_start:Ptr[Int], value_end:Ptr[Int], 
                       line_len:Ptr[Int]):Int = {
    !line_len = -1
    val scan_result = stdio.sscanf(line, c"%*[^\r\n:]%n: %n%*[^\r\n]%n%*[\r\n]%n", 
      key_end, value_start, value_end, line_len)
    if (!line_len != -1) {
      val start_of_key = line
      val end_of_key = line + !key_end
      !end_of_key = 0
      val start_of_value = line + !value_start
      val end_of_value = line + !value_end
      !end_of_value = 0
      val key = fromCString(start_of_key)
      val value = fromCString(start_of_value)
      out_map(key) = value
      !line_len
    } else {
      throw new Exception("bad header line")
    }
  }

  val line_buffer = malloc(1024)
  
  def parseRequest(req:CString, size: Long):HttpRequest = {
    req(size) = 0 // ensure null termination
    var req_position = req
    // val line_buffer = stackalloc[CChar](1024)
    val line_len = stackalloc[Int]
    val key_end = stackalloc[Int]
    val value_start = stackalloc[Int]
    val value_end = stackalloc[Int]
    val headers = mutable.Map[String,String]()

    val (method,uri,request_len) = scan_request_line(req)

    var bytes_read = request_len
    while (bytes_read < size) {
      req_position = req + bytes_read
      val parse_header_result = scan_header_line(req_position, headers,
        key_end, value_start, value_end, line_len)
      if (parse_header_result < 0) {
        throw new Exception("HEADERS INCOMPLETE")
      } else if (!line_len - !value_end == 2) {
        bytes_read += parse_header_result
      } else if (!line_len - !value_end == 4) {
        val remaining = size - bytes_read
        val body = fromCString(req + bytes_read)
        return HttpRequest(method,uri,headers,body)
      } else {
        throw new Exception("malformed header!")
      }
    }
    throw new Exception(s"bad scan, exceeded $size bytes")
  }

val key_buffer = malloc(512)
val value_buffer = malloc(512)
val body_buffer = malloc(4096)

def make_response(response:HttpResponse, buffer:Ptr[Buffer]):Unit = {
  stdio.snprintf(buffer._1,4096,c"HTTP/1.1 200 OK\r\n")
  var header_pos = 0
  val buffer_start = buffer._1
  var bytes_written = strlen(buffer_start)
  var last_pos = buffer_start + bytes_written
  var bytes_remaining = 4096 - bytes_written
  val headers = response.headers.keys.toSeq
  while (header_pos < response.headers.size) {
    val k = headers(header_pos)
    val v = response.headers(k)
    Zone { implicit z =>
      val k_tmp = toCString(k)
      val v_tmp = toCString(v)
      strncpy(key_buffer,k_tmp,512)
      strncpy(value_buffer,v_tmp,512)
    }
    stdio.snprintf(last_pos,bytes_remaining.toInt,c"%s: %s\r\n",key_buffer,value_buffer)
    val len = strlen(last_pos)
    bytes_written = bytes_written + len + 1
    bytes_remaining = 4096 - bytes_written
    last_pos = last_pos + len
    header_pos += 1
  }
  Zone { implicit z =>
    val body = toCString(response.body)
    val body_len = strlen(body)  
    strncpy(body_buffer,body,4096)
  }
  stdio.snprintf(last_pos,bytes_remaining.toInt,c"\r\n%s", body_buffer)
}
}
