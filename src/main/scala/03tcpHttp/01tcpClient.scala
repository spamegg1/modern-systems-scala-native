package `03tcp`

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.*
import scalanative.libc.*
// import scalanative.native.*
import stdio.*, string.*, stdlib.*
import scalanative.posix.unistd.*
import scalanative.posix.sys.socket.*
import scalanative.posix.netinet.in.*
import scalanative.posix.arpa.inet.*
import collection.mutable
import scalanative.posix.netdb.*
import scalanative.posix.netdbOps.*

def makeConnection(address: CString, port: CString): Int =
  val hints: Ptr[addrinfo] = stackalloc[addrinfo](sizeof[addrinfo])
  string.memset(hints.asInstanceOf[Ptr[Byte]], 0, sizeof[addrinfo])
  hints.ai_family = AF_UNSPEC
  hints.ai_socktype = SOCK_STREAM

  val addrInfoPtr: Ptr[Ptr[addrinfo]] =
    stackalloc[Ptr[addrinfo]](sizeof[Ptr[addrinfo]])
  println("about to perform lookup")

  val lookupResult = getaddrinfo(address, port, hints, addrInfoPtr)
  println(s"lookup returned ${lookupResult}")

  if lookupResult != 0 then
    val errString = util.gai_strerror(lookupResult)
    stdio.printf(c"errno: %d %s\n", lookupResult, errString)
    throw new Exception("no address found")
  else
    val addrInfo = !addrInfoPtr
    stdio.printf(
      c"""got addrinfo: flags %d, family %d, socktype %d,
      protocol %d\n""",
      addrInfo.ai_family,
      addrInfo.ai_flags,
      addrInfo.ai_socktype,
      addrInfo.ai_protocol
    )

    println("creating socket")
    val sock =
      socket(addrInfo.ai_family, addrInfo.ai_socktype, addrInfo.ai_protocol)
    println(s"socket returned fd $sock")
    if sock < 0 then throw new Exception("error in creating socket")

    println("connecting")
    val connectResult = connect(sock, addrInfo.ai_addr, addrInfo.ai_addrlen)
    println(s"connect returned $connectResult")

    if connectResult != 0 then
      val err = errno.errno
      val errString = string.strerror(err)
      stdio.printf(c"errno: %d %s\n", err, errString)
      throw new Exception("connection failed")

    sock

def makeRequest(sock: Ptr[FILE], request: String): String =
  val responseBuffer = stdlib.malloc(2048.toULong)
  val response = Zone { implicit z =>
    val requestCstring = toCString(request)
    stdio.fprintf(sock, requestCstring)
    val responseCstring = fgets(responseBuffer, 4095, sock)
    fromCString(responseBuffer)
  }
  stdlib.free(responseBuffer)
  response

def handleConnection(sock: Int): Unit =
  val responseBuffer = malloc(4096.toULong)
  val socketFileDescriptor = util.fdopen(sock, c"r+")
  val resp = makeRequest(socketFileDescriptor, "hello?  is anybody there?\n")
  println(s"I got a response: ${resp.trim()}")
  fclose(socketFileDescriptor)
  println("done")

// uncomment @main, make sure all other @main s in other files are commented
// use sbt> nativeLink to create executable
// run it in two Terminal windows with:
// nc -l -v 127.0.0.1 8080
// ./target/scala-3.2.2/scala-native-out 127.0.0.1 8080
// looking up address: 127.0.0.1 port: 8080
// about to perform lookup
// lookup returned 0
// got addrinfo: flags 2, family 0, socktype 1,
//       protocol 6
// creating socket
// socket returned fd 3
// connecting
// connect returned 0
// I got a response: asd
// done
// @main
def tcpClient(args: String*): Unit =
  if args.length != 2 then
    println("Usage: ./tcp_test [address] [port]")
    ()

  val sock = Zone { implicit z =>
    val address = toCString(args(0))
    val port = toCString(args(1))
    stdio.printf(c"looking up address: %s port: %s\n", address, port)
    makeConnection(address, port)
  }

  handleConnection(sock)

@extern
object util:
  def getaddrinfo(
      address: CString,
      port: CString,
      hints: Ptr[addrinfo],
      res: Ptr[Ptr[addrinfo]]
  ): Int = extern
  def socket(family: Int, socktype: Int, protocol: Int): Int = extern
  def connect(sock: Int, addrInfo: Ptr[sockaddr], addrLen: CInt): Int = extern
  def gai_strerror(code: Int): CString = extern
  def fdopen(fd: Int, mode: CString): Ptr[FILE] = extern
