package ch03

import scalanative.posix.sys.socket.{AF_UNSPEC, SOCK_STREAM, socket, sockaddr, connect}
import scalanative.posix.netdb.{addrinfo, getaddrinfo}
import scalanative.posix.netdbOps.addrinfoOps // ai_family, ai_socktype
import scalanative.unsafe.{Zone, Ptr, CInt, CString, toCString, fromCString, CQuote}
import scalanative.unsafe.{stackalloc, sizeof, extern}
import scalanative.libc.{stdio, stdlib, string, errno}
import stdio.{FILE, fgets, fclose}

// used in main
def makeConnection(address: CString, port: CString): Int =
  // provide hints to getaddrinfo. Zero all the addrinfo fields, only populate a few.
  val hints: Ptr[addrinfo] = stackalloc[addrinfo](1)
  string.memset[addrinfo](hints, 0, sizeof[addrinfo]) // dont give bad hints
  hints.ai_family = AF_UNSPEC // unspecified, could be ipv4 or ipv6
  hints.ai_socktype = SOCK_STREAM // stream for TCP, dgram for UDP

  val addrInfoPtr: Ptr[Ptr[addrinfo]] = stackalloc[Ptr[addrinfo]](1) // to store result
  println("about to perform lookup")

  val lookupResult = getaddrinfo(address, port, hints, addrInfoPtr) // lookup!
  println(s"lookup returned ${lookupResult}")

  if lookupResult != 0 then // lookup failed
    val errString = util.gai_strerror(lookupResult) // get error
    stdio.printf(c"errno: %d %s\n", lookupResult, errString) // show error
    throw Exception("no address found") // crash
  else // lookup successful
    val addrInfo: Ptr[addrinfo] = !addrInfoPtr
    stdio.printf(
      c"got addrinfo: flags %d, family %d, socktype %d, protocol %d\n",
      addrInfo.ai_family, // ipv4 or ipv6
      addrInfo.ai_flags,
      addrInfo.ai_socktype, // stream or dgram
      addrInfo.ai_protocol
    )

    println("creating socket")
    val sock = socket(addrInfo.ai_family, addrInfo.ai_socktype, addrInfo.ai_protocol)
    println(s"socket returned fd $sock")
    if sock < 0 then throw Exception("error in creating socket") // socket failed

    println("connecting")
    val connectResult = connect(sock, addrInfo.ai_addr, addrInfo.ai_addrlen)
    println(s"connect returned $connectResult")

    if connectResult != 0 then // connection failed
      val err = errno.errno
      val errString = string.strerror(err)
      stdio.printf(c"errno: %d %s\n", err, errString)
      throw Exception("connection failed")

    sock

@extern
object util:
  // on Linux, man getaddrinfo gives info on getaddrinfo, freeaddrinfo, gai_strerror
  def getaddrinfo(
      address: CString, // hostname to lookup, e.g. "www.pragprog.com"
      port: CString, // port number, e.g. "80"
      hints: Ptr[addrinfo], // partially populated addrinfo struct object
      res: Ptr[Ptr[addrinfo]] // to hold the result
  ): Int = extern
  def gai_strerror(code: Int): CString = extern
  def freeaddrinfo(res: Ptr[addrinfo]): Unit = extern

  def socket(family: Int, socktype: Int, protocol: Int): Int = extern // man socket
  def connect(sock: Int, addrInfo: Ptr[sockaddr], addrLen: CInt): Int = extern

  // open socket file descriptors as FILE, then close
  def fdopen(fd: Int, mode: CString): Ptr[FILE] = extern
  def fclose(file: Ptr[FILE]): Int = extern

  // Scala Native provides accessors to fields, so we don't have to remember offsets.
  // type addrinfo = CStruct8[
  //   CInt, // ai_flags
  //   CInt, // ai_family
  //   CInt, // ai_socktype
  //   CInt, // ai_protocol
  //   socklen_t, // ai_addrlen
  //   Ptr[sockaddr], // ai_addr
  //   Ptr[CChar], // ai_canonname
  //   Ptr[addrinfo] // ai_next: linked list, if name resolved to multiple IP addresses.
  // ]
