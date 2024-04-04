package ch03.tcp

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

// used in handleConnection
def makeRequest(sock: Ptr[FILE], request: String): String =
  // sending request and receiving response thru socket is low-level (C), so allocate.
  // 4096 bytes is a good size for our response string.
  val responseBuffer = stdlib.malloc(4096) // 0.5

  val response: String = Zone: // implicit z => // 0.5, we don't need implicit z anymore
    val requestCstring: CString = toCString(request) // requires a Zone
    stdio.fprintf(sock, requestCstring) // write request to socket stream, send

    // receive response from server through same socket stream (bidirectional)
    val responseCstring = fgets(responseBuffer, 4096 - 1, sock) // 4096 is an OK size.
    fromCString(responseBuffer) // does not require a Zone

  stdlib.free(responseBuffer)
  response

// used in main
def handleConnection(sock: Int): Unit =
  val socketFileDescriptor: Ptr[FILE] = util.fdopen(sock, c"r+") // convert socket to FILE

  val resp: String = makeRequest(socketFileDescriptor, "hello? is anybody there?\n")
  println(s"I got a response: ${resp.trim()}")

  fclose(socketFileDescriptor)
  println("done")

// use netcat in one terminal session: nc -l -v 127.0.0.1 8080
// use netcat in second terminal: nc -v 127.0.0.1 8080
// first terminal:
// nc -l -v 127.0.0.1 8080
// Listening on localhost 8080
// Connection received on localhost 33220
// asd
// text from here is copied over there!
// what about the other direction?
// Yep, it's bi-directional.
// second terminal:
// nc -v 127.0.0.1 8080
// Connection to 127.0.0.1 8080 port [tcp/http-alt] succeeded!
// asd
// text from here is copied over there!
// what about the other direction?
// Yep, it's bi-directional.

// Compile with: scala-cli 01tcpClient.scala --native --native-version 0.5.0-RC2
// On the server-side:
// nc -l -v 127.0.0.1 8080
// Listening on localhost 8080
// Connection received on localhost 48390
// hello? is anybody there?
// yep!
// On the client-side:
// ./.scala-build/Templates_25019ee6b9-c9c053f2dd/native/main 127.0.0.1 8080
// looking up address: 127.0.0.1 port: 8080
// about to perform lookup
// lookup returned 0
// got addrinfo: flags 2, family 0, socktype 1, protocol 6
// creating socket
// socket returned fd 3
// connecting
// connect returned 0
// I got a response: yep!
// done
@main
def tcpClient(args: String*): Unit =
  if args.length != 2 then
    println("Usage: ./tcp_test [address] [port]")
    () // exit
  else
    val sock = Zone: // implicit z => // 0.5
      val (address, port) = (toCString(args(0)), toCString(args(1))) // requires Zone
      stdio.printf(c"looking up address: %s port: %s\n", address, port)
      makeConnection(address, port) // looks up address, returns socket
    handleConnection(sock) // gets response from socket and prints it.

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
