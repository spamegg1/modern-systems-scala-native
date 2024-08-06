package ch07

import scalanative.unsafe.*

@link("curl")
@extern
object LibCurl:
  type Curl = Ptr[Byte]
  type IdleHandle = Ptr[Byte] // made it up! to make some examples compile.
  type CurlBuffer = CStruct2[CString, CSize]
  type CurlOption = CInt // Int?
  type CurlAction = CInt
  type CurlInfo = CInt
  type MultiCurl = Ptr[Byte]
  type CurlRequest = CStruct4[Ptr[Byte], Long, Long, Int]
  type CurlMessage = CStruct3[Int, Curl, Ptr[Byte]]
  type CurlSList = CStruct2[Ptr[Byte], CString] // linked list for Http headers
  type CurlDataCallback = CFuncPtr4[Ptr[Byte], CSize, CSize, Ptr[Byte], CSize]
  type CurlSocketCallback = CFuncPtr5[Curl, Ptr[Byte], CInt, Ptr[Byte], Ptr[Byte], CInt]
  type CurlTimerCallback = CFuncPtr3[MultiCurl, Long, Ptr[Byte], CInt]
  type SocketCallback = CFuncPtr5[Curl, Ptr[Byte], CurlAction, Ptr[Byte], Ptr[Byte], CInt]
  type TimerCallback = CFuncPtr3[MultiCurl, Long, Ptr[Byte], CInt]

  def curl_easy_strerror(code: Int): CString = extern
  @name("curl_global_init") def global_init(flags: Long): Unit = extern
  @name("curl_global_cleanup") def global_cleanup(): Unit = extern
  @name("curl_easy_init") def easy_init(): Curl = extern
  @name("curl_easy_cleanup") def easy_cleanup(handle: Curl): Unit = extern
  @name("curl_easy_perform") def easy_perform(easy_handle: Curl): CInt = extern
  @name("curl_multi_init") def multi_init(): MultiCurl = extern
  @name("curl_slist_free_all") def slist_free_all(slist: Ptr[CurlSList]): Unit = extern
  @name("curl_multi_cleanup") def multi_cleanup(multi: MultiCurl): Int = extern

  @name("curl_easy_setopt")
  def curl_easy_setopt(handle: Curl, option: CurlOption, parameter: CVarArgList): CInt =
    extern

  @name("curl_easy_setopt") // convert URL to CString, pass it to easy_setopt
  def curl_easy_setopt(handle: Curl, option: CurlOption, parameter: Ptr[Byte]): CInt =
    extern

  @name("curl_easy_getinfo")
  def easy_getinfo(handle: Curl, info: CurlInfo, parameter: Ptr[Byte]): CInt = extern

  @name("curl_multi_add_handle")
  def multi_add_handle(multi: MultiCurl, easy: Curl): Int = extern

  @name("curl_multi_setopt")
  def curl_multi_setopt(multi: MultiCurl, option: CInt, parameter: CVarArg): CInt = extern

  @name("curl_multi_setopt")
  def multi_setopt_ptr(multi: MultiCurl, option: CInt, parameter: Ptr[Byte]): CInt =
    extern

  @name("curl_multi_assign")
  def multi_assign(multi: MultiCurl, socket: Ptr[Byte], socket_data: Ptr[Byte]): Int =
    extern

  @name("curl_multi_socket_action")
  def multi_socket_action(
      multi: MultiCurl,
      socket: Ptr[Byte],
      events: Int,
      numhandles: Ptr[Int]
  ): Int = extern

  @name("curl_multi_info_read")
  def multi_info_read(multi: MultiCurl, message: Ptr[Int]): Ptr[CurlMessage] = extern

  @name("curl_multi_perform")
  def multi_perform(multi: MultiCurl, numhandles: Ptr[Int]): Int = extern

  @name("curl_slist_append")
  def slist_append(slist: Ptr[CurlSList], string: CString): Ptr[CurlSList] = extern
