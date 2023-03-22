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
import scala.collection.mutable
import scala.scalanative.libc.stdlib._
import scala.scalanative.libc.string._
import scala.concurrent._
import scala.scalanative.runtime.Boxes
import scala.scalanative.runtime.Intrinsics

case class ResponseState(
  var code:Int = 200,
  var headers:mutable.Map[String,String] = mutable.Map(),
  var body:String = ""
)

object Curl {
  import LibCurl._
  import LibCurlConstants._
  import LibUV._
  import LibUVConstants._
  def func_to_ptr(f:Object):Ptr[Byte] = {
    Boxes.boxToPtr[Byte](Boxes.unboxToCFuncRawPtr(f))
  }

  def int_to_ptr(i:Int):Ptr[Byte] = {
    Boxes.boxToPtr[Byte](Intrinsics.castIntToRawPtr(i))
  }

  def long_to_ptr(l:Long):Ptr[Byte] = {
    Boxes.boxToPtr[Byte](Intrinsics.castLongToRawPtr(l))
  }

}
object LibCurlConstants {
  import LibCurl._
  val URL:CurlOption = 10002
  val PORT:CurlOption = 10003
  val USERPASSWORD:CurlOption = 10005

  val READDATA:CurlOption = 10009
  val HEADERDATA:CurlOption = 10029
  val WRITEDATA:CurlOption = 10001

  val READCALLBACK:CurlOption = 20012
  val HEADERCALLBACK:CurlOption = 20079
  val WRITECALLBACK:CurlOption = 20011

  val TIMEOUT:CurlOption = 13
  val GET:CurlOption = 80
  val POST:CurlOption = 47
  val PUT:CurlOption = 54
  val CONTENTLENGTHDOWNLOADT:CurlInfo = 0x300000 + 15
  val HTTPHEADER:CurlOption = 10023

  val PRIVATEDATA:CurlOption = 10103
  val GET_PRIVATEDATA:CurlInfo = 0x100000 + 21

  val SOCKETFUNCTION:CurlOption = 20001
  type SocketCallback = CFuncPtr5[Curl, Ptr[Byte], CInt, Ptr[Byte], Ptr[Byte], CInt]
  val TIMERFUNCTION:CurlOption = 20004
  type TimerCallback = CFuncPtr3[MultiCurl, Long, Ptr[Byte], CInt]

  type CurlAction = CInt
  val POLL_NONE:CurlAction = 0
  val POLL_IN:CurlAction = 1
  val POLL_OUT:CurlAction = 2
  val POLL_INOUT:CurlAction = 3
  val POLL_REMOVE:CurlAction = 4
}

@link("curl")
@extern object LibCurl {
  type CurlBuffer = CStruct2[CString, CSize]

  type Curl = Ptr[Byte]
  type CurlOption = Int
  type CurlInfo = CInt

  @name("curl_global_init")
  def global_init(flags:Long):Unit = extern

  @name("curl_easy_init")
  def easy_init():Curl = extern

  @name("curl_easy_setopt")
  def curl_easy_setopt(handle: Curl, option: CInt, 
                       parameter: Ptr[Byte]): CInt = extern

  @name("curl_easy_getinfo")
  def easy_getinfo(handle: Curl, info: CInt, 
                   parameter: Ptr[Byte]): CInt = extern

  @name("curl_easy_perform")
  def easy_perform(easy_handle: Curl): CInt = extern

  type CurlSList = CStruct2[Ptr[Byte],CString]

  @name("curl_slist_append")
  def slist_append(slist:Ptr[CurlSList], string:CString):Ptr[CurlSList] = extern

  @name("curl_slist_free_all")
  def slist_free_all(slist:Ptr[CurlSList]):Unit = extern

  @name("curl_global_cleanup")
  def global_cleanup():Unit = extern

  @name("curl_easy_cleanup")
  def easy_cleanup(handle: Curl): Unit = extern

  type CurlRequest = CStruct4[Ptr[Byte],Long,Long,Int]
  type CurlMessage = CStruct3[Int, Curl, Ptr[Byte]]

  type CurlDataCallback = CFuncPtr4[Ptr[Byte],CSize,CSize,Ptr[Byte],CSize]
  type CurlSocketCallback = CFuncPtr5[Curl, Ptr[Byte], CInt, Ptr[Byte], Ptr[Byte], CInt]
  type CurlTimerCallback = CFuncPtr3[MultiCurl, Long, Ptr[Byte], CInt]

  type MultiCurl = Ptr[Byte]

  @name("curl_multi_init")
  def multi_init():MultiCurl = extern

  @name("curl_multi_add_handle")
  def multi_add_handle(multi:MultiCurl, easy:Curl):Int = extern

  @name("curl_multi_setopt")
  def curl_multi_setopt(multi:MultiCurl, option:CInt, parameter:CVarArg):CInt = extern

  @name("curl_multi_setopt")
  def multi_setopt_ptr(multi:MultiCurl, option:CInt, parameter:Ptr[Byte]):CInt = extern

  @name("curl_multi_assign")
  def multi_assign(
    multi:MultiCurl,
    socket:Ptr[Byte],
    socket_data:Ptr[Byte]):Int = extern

  @name("curl_multi_socket_action")
  def multi_socket_action(
    multi:MultiCurl,
    socket:Ptr[Byte],
    events:Int,
    numhandles:Ptr[Int]):Int = extern

  @name("curl_multi_info_read")
  def multi_info_read(multi:MultiCurl, message:Ptr[Int]):Ptr[CurlMessage] = extern

  @name("curl_multi_perform")
  def multi_perform(multi:MultiCurl, numhandles:Ptr[Int]):Int = extern

  @name("curl_multi_cleanup")
  def multi_cleanup(multi:MultiCurl):Int = extern


  def curl_easy_strerror(code:Int):CString = extern
}
