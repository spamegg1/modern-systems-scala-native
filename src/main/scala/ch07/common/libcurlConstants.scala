package ch07

import scalanative.unsafe.{CInt, Ptr, CFuncPtr3, CFuncPtr5}

object LibCurlConstants:
  import LibCurl.*

  val URL = 10002
  val PORT = 10003
  val USERPASSWORD = 10005

  val READDATA = 10009
  val HEADERDATA = 10029
  val WRITEDATA = 10001

  val READCALLBACK = 20012
  val HEADERCALLBACK = 20079
  val WRITECALLBACK = 20011

  val TIMEOUT = 13
  val GET = 80
  val POST = 47
  val PUT = 54
  val CONTENTLENGTHDOWNLOADT = 0x300000 + 15
  val HTTPHEADER = 10023

  val PRIVATEDATA = 10103
  val GET_PRIVATEDATA = 0x100000 + 21

  val SOCKETFUNCTION = 20001
  type SocketCallback = CFuncPtr5[Curl, Ptr[Byte], CInt, Ptr[Byte], Ptr[Byte], CInt]
  val TIMERFUNCTION = 20004
  type TimerCallback = CFuncPtr3[MultiCurl, Long, Ptr[Byte], CInt]

  type CurlAction = CInt
  val POLL_NONE: CurlAction = 0
  val POLL_IN: CurlAction = 1
  val POLL_OUT: CurlAction = 2
  val POLL_INOUT: CurlAction = 3
  val POLL_REMOVE: CurlAction = 4
