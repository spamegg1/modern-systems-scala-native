package ch10.libUvService

import scalanative.unsafe.*
import scalanative.libc.*
import scalanative.unsigned.*
import stdlib.*, stdio.*, string.*
import collection.mutable

case class RequestState(
    url: String,
    method: String,
    var lastHeader: String = "None",
    headerMap: mutable.Map[String, String] = mutable.Map[String, String](),
    var body: String = ""
)

trait Parsing:
  import LibUV.*, HttpParser.*
  val requests: mutable.Map[Long, RequestState]

  def handleRequest(id: Long, handle: TCPHandle, request: RequestState): Unit

  type ConnectionState = CStruct3[Long, TCPHandle, Parser]

  val HTTP_REQUEST = 0
  val HTTP_RESPONSE = 1
  val HTTP_BOTH = 2

  def bytesToString(data: Ptr[Byte], len: Long): String =
    val bytes = new Array[Byte](len.toInt)
    var c = 0
    while c < len do
      bytes(c) = !(data + c)
      c += 1

    String(bytes)

  def onURL(p: Ptr[Parser], data: CString, len: Long): Int =
    val state = (p._8).asInstanceOf[Ptr[ConnectionState]]
    val messageId = state._1
    val url = bytesToString(data, len)
    println(s"got url: $url")
    val m = p._6
    val method = fromCString(http_method_str(m))
    println(s"method: $method ($m)")
    requests(messageId) = RequestState(url, method)
    0

  def onHeaderKey(p: Ptr[Parser], data: CString, len: Long): Int =
    val state = p._8.asInstanceOf[Ptr[ConnectionState]]
    val messageId = state._1
    val request = requests(messageId)

    val key = bytesToString(data, len)
    request.lastHeader = key
    requests(messageId) = request
    0

  def onHeaderValue(p: Ptr[Parser], data: CString, len: Long): Int =
    val state = p._8.asInstanceOf[Ptr[ConnectionState]]
    val messageId = state._1
    val request = requests(messageId)

    val value = bytesToString(data, len)
    request.headerMap(request.lastHeader) = value
    requests(messageId) = request
    0

  def onBody(p: Ptr[Parser], data: CString, len: Long): Int =
    val state = p._8.asInstanceOf[Ptr[ConnectionState]]
    val messageId = state._1
    val request = requests(messageId)

    val body = bytesToString(data, len)
    request.body += body
    requests(messageId) = request
    0

  def onMessageComplete(p: Ptr[Parser]): Int =
    val state = p._8.asInstanceOf[Ptr[ConnectionState]]
    val messageId = state._1
    val tcpHandle = state._2
    val request = requests(messageId)
    handleRequest(messageId, tcpHandle, request)
    0

@link("http_parser")
@extern
object HttpParser:
  type Parser = CStruct8[
    Long, // private data
    Long, // private data
    UShort, // major version
    UShort, // minor version
    UShort, // status (request only)
    CChar, // method
    CChar, // Error (last bit upgrade)
    Ptr[Byte] // user data
  ]

  type HttpCB = CFuncPtr1[Ptr[Parser], Int]
  type HttpDataCB = CFuncPtr3[Ptr[Parser], CString, Long, Int]

  type ParserSettings = CStruct8[
    HttpCB, // on_message_begin
    HttpDataCB, // on_url
    HttpDataCB, // on_status
    HttpDataCB, // on_header_field
    HttpDataCB, // on_header_value
    HttpCB, // on_headers_complete
    HttpDataCB, // on_body
    HttpCB // on_message_complete
  ]

  def http_parser_init(p: Ptr[Parser], parser_type: Int): Unit = extern
  def http_parser_settings_init(s: Ptr[ParserSettings]): Unit = extern
  def http_parser_execute(
      p: Ptr[Parser],
      s: Ptr[ParserSettings],
      data: Ptr[Byte],
      len: Long
  ): Long = extern
  def http_method_str(method: CChar): CString = extern
