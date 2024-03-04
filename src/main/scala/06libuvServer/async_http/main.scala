package `06libuvHttp`

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.*
import scalanative.libc.*
import stdlib.malloc

import LibUV.*, LibUVConstants.*
import HTTP.RequestHandler

type ClientState = CStruct3[Ptr[Byte], CSize, CSize]

// @main
def asyncHttp(args: String): Unit =
  serveHttp(
    8080,
    request => HttpResponse(200, Map("Content-Length" -> "12"), "hello world\n")
  )

var router: RequestHandler = (_ => ???)

def serveHttp(port: Int, handler: RequestHandler): Unit =
  println(s"about to serve on port ${port}")
  this.router = handler
  serveTcp(c"0.0.0.0", port, 0, 4096, connectionCB)

// val readCB = new ReadCB:
val readCB = CFuncPtr3.fromScalaFunction[TCPHandle, CSSize, Ptr[Buffer], Unit](
  (client: TCPHandle, size: CSSize, buffer: Ptr[Buffer]) =>
    if size < 0 then shutdown(client)
    else
      try
        val parsedRequest = HTTP.parseRequest(buffer._1, size.toLong) // 0.5
        val response = router(parsedRequest)
        sendResponse(client, response)
        shutdown(client)
      catch
        case e: Throwable =>
          println(s"error during parsing: ${e}")
          shutdown(client)
)

def sendResponse(client: TCPHandle, response: HttpResponse): Unit =
  val req = malloc(uv_req_size(UV_WRITE_REQ_T)).asInstanceOf[WriteReq]
  val responseBuffer = malloc(sizeof[Buffer]).asInstanceOf[Ptr[Buffer]]

  responseBuffer._1 = malloc(4096.toUSize) // 0.5
  responseBuffer._2 = 4096.toUSize // 0.5

  HTTP.makeResponse(response, responseBuffer)
  responseBuffer._2 = string.strlen(responseBuffer._1)

  !req = responseBuffer.asInstanceOf[Ptr[Byte]]
  checkError(uv_write(req, client, responseBuffer, 1, writeCB), "uv_write")

val loop = uv_default_loop()

def serveTcp(
    address: CString,
    port: Int,
    flags: Int,
    backlog: Int,
    callback: ConnectionCB
): Unit = {
  val addr = stackalloc[Byte](sizeof[Byte])
  val addrConvert = uv_ip4_addr(address, port, addr)
  println(s"uv_ip4_addr returned $addrConvert")

  val handle = malloc(uv_handle_size(UV_TCP_T)).asInstanceOf[TCPHandle]
  checkError(uv_tcp_init(loop, handle), "uv_tcp_init(server)")
  checkError(uv_tcp_bind(handle, addr, flags), "uv_tcp_bind")
  checkError(uv_listen(handle, backlog, callback), "uv_tcp_listen")

  uv_run(loop, UV_RUN_DEFAULT)
  ()
}

// val connectionCB = new ConnectionCB:
val connectionCB =
  CFuncPtr2.fromScalaFunction[TCPHandle, Int, Unit]((handle: TCPHandle, status: Int) =>
    println("received connection")

    // initialize the new client tcp handle and its state
    val client = malloc(uv_handle_size(UV_TCP_T)).asInstanceOf[TCPHandle]
    checkError(uv_tcp_init(loop, client), "uv_tcp_init(client)")

    var clientStatePtr = (!client).asInstanceOf[Ptr[ClientState]]
    clientStatePtr = initializeClientState(client)

    // accept the incoming connection into the new handle
    checkError(uv_accept(handle, client), "uv_accept")
    // set up callbacks for incoming data
    checkError(uv_read_start(client, allocCB, readCB), "uv_read_start")
  )

def initializeClientState(client: TCPHandle): Ptr[ClientState] =
  val clientStatePtr =
    malloc(sizeof[ClientState]).asInstanceOf[Ptr[ClientState]]

  stdio.printf(
    c"allocated data at %x; assigning into handle storage at %x\n",
    clientStatePtr,
    client
  )

  val clientStateData = malloc(4096.toUSize) // 0.5
  clientStatePtr._1 = clientStateData
  clientStatePtr._2 = 4096.toUSize // total // 0.5
  clientStatePtr._3 = 0.toUSize // used // 0.5

  !client = clientStatePtr.asInstanceOf[Ptr[Byte]]
  clientStatePtr

// val allocCB = new AllocCB:
val allocCB = CFuncPtr3.fromScalaFunction[TCPHandle, CSize, Ptr[Buffer], Unit](
  (client: TCPHandle, size: CSize, buffer: Ptr[Buffer]) =>
    println("allocating 4096 bytes")
    val buf = malloc(4096.toUSize) // 0.5
    buffer._1 = buf
    buffer._2 = 4096.toUSize // 0.5
)

def appendData(
    state: Ptr[ClientState],
    size: CSSize,
    buffer: Ptr[Buffer]
): Unit =
  val copyPosition = state._1 + state._3
  string.strncpy(copyPosition, buffer._1, size.toUSize) // 0.5

  // be sure to update the length of the data since we have copied into it
  state._3 = state._3 + size.toUSize // 0.5

  stdio.printf(c"client %x: %d/%d bytes used\n", state, state._3, state._2)

def sendResponse(client: TCPHandle, state: Ptr[ClientState]): Unit =
  val resp = malloc(uv_req_size(UV_WRITE_REQ_T)).asInstanceOf[WriteReq]
  val responseBuffer = malloc(sizeof[Buffer]).asInstanceOf[Ptr[Buffer]]
  responseBuffer._1 = makeResponse(state)
  responseBuffer._2 = string.strlen(responseBuffer._1)
  !resp = responseBuffer.asInstanceOf[Ptr[Byte]]
  checkError(uv_write(resp, client, responseBuffer, 1, writeCB), "uv_write")

def makeResponse(state: Ptr[ClientState]): CString =
  val responseFormat = c"received response:\n%s\n"
  val responseData = malloc(string.strlen(responseFormat) + state._3)
  stdio.sprintf(responseData, responseFormat, state._1)
  responseData

// val writeCB = new WriteCB:
val writeCB =
  CFuncPtr2.fromScalaFunction[WriteReq, Int, Unit]((writeReq: WriteReq, status: Int) =>
    println("write completed")
    val responseBuffer = (!writeReq).asInstanceOf[Ptr[Buffer]]
    stdlib.free(responseBuffer._1)
    stdlib.free(responseBuffer.asInstanceOf[Ptr[Byte]])
    stdlib.free(writeReq.asInstanceOf[Ptr[Byte]])
  )

def shutdown(client: TCPHandle): Unit =
  val shutdownRequest =
    malloc(uv_req_size(UV_shutdownRequest_T)).asInstanceOf[ShutdownReq]
  !shutdownRequest = client.asInstanceOf[Ptr[Byte]]
  checkError(uv_shutdown(shutdownRequest, client, shutdownCB), "uv_shutdown")

// val shutdownCB = new ShutdownCB:
val shutdownCB = CFuncPtr2.fromScalaFunction[ShutdownReq, Int, Unit](
  (shutdownReq: ShutdownReq, status: Int) =>
    println("all pending writes complete, closing TCP connection")
    val client = (!shutdownReq).asInstanceOf[TCPHandle]
    checkError(uv_close(client, closeCB), "uv_close")
    stdlib.free(shutdownReq.asInstanceOf[Ptr[Byte]])
)

// val closeCB = new CloseCB:
val closeCB =
  CFuncPtr1.fromScalaFunction[TCPHandle, Unit]((client: TCPHandle) =>
    println("closed client connection")
    val clientStatePtr = (!client).asInstanceOf[Ptr[ClientState]]
    stdlib.free(clientStatePtr._1)
    stdlib.free(clientStatePtr.asInstanceOf[Ptr[Byte]])
    stdlib.free(client.asInstanceOf[Ptr[Byte]])
  )

@link("uv")
@extern
object LibUV:
  type TimerHandle = Ptr[Byte]
  type Loop = Ptr[Byte]
  type TimerCB = CFuncPtr1[TimerHandle, Unit]

  def uv_default_loop(): Loop = extern
  def uv_loop_size(): CSize = extern
  def uv_is_active(handle: Ptr[Byte]): Int = extern
  def uv_handle_size(h_type: Int): CSize = extern
  def uv_req_size(r_type: Int): CSize = extern

  def uv_timer_init(loop: Loop, handle: TimerHandle): Int = extern
  def uv_timer_start(
      handle: TimerHandle,
      cb: TimerCB,
      timeout: Long,
      repeat: Long
  ): Int = extern
  def uv_timer_stop(handle: TimerHandle): Int = extern

  def uv_run(loop: Loop, runMode: Int): Int = extern

  def uv_strerror(err: Int): CString = extern
  def uv_err_name(err: Int): CString = extern

  type Buffer = CStruct2[Ptr[Byte], CSize]
  type TCPHandle = Ptr[Ptr[Byte]]
  type ConnectionCB = CFuncPtr2[TCPHandle, Int, Unit] // can't use!
  type WriteReq = Ptr[Ptr[Byte]]
  type ShutdownReq = Ptr[Ptr[Byte]]

  def uv_tcp_init(loop: Loop, tcp_handle: TCPHandle): Int = extern
  def uv_tcp_bind(tcp_handle: TCPHandle, address: Ptr[Byte], flags: Int): Int =
    extern
  def uv_listen(
      stream_handle: TCPHandle,
      backlog: Int,
      uv_connection_cb: ConnectionCB
  ): Int = extern
  def uv_accept(server: TCPHandle, client: TCPHandle): Int = extern
  def uv_read_start(client: TCPHandle, allocCB: AllocCB, readCB: ReadCB): Int =
    extern
  def uv_write(
      writeReq: WriteReq,
      client: TCPHandle,
      bufs: Ptr[Buffer],
      numBufs: Int,
      writeCB: WriteCB
  ): Int = extern
  def uv_shutdown(
      shutdownReq: ShutdownReq,
      client: TCPHandle,
      shutdownCB: ShutdownCB
  ): Int = extern
  def uv_close(handle: TCPHandle, closeCB: CloseCB): Int = extern

  def uv_ip4_addr(address: CString, port: Int, out_addr: Ptr[Byte]): Int =
    extern
  def uv_ip4_name(address: Ptr[Byte], s: CString, size: Int): Int = extern

  // can't use these anymore! too bad.
  type AllocCB = CFuncPtr3[TCPHandle, CSize, Ptr[Buffer], Unit]
  type ReadCB = CFuncPtr3[TCPHandle, CSSize, Ptr[Buffer], Unit]
  type WriteCB = CFuncPtr2[WriteReq, Int, Unit]
  type ShutdownCB = CFuncPtr2[ShutdownReq, Int, Unit]
  type CloseCB = CFuncPtr1[TCPHandle, Unit]

object LibUVConstants:
  import LibUV.*

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
  val UV_shutdownRequest_T = 4

  val UV_READABLE = 1
  val UV_WRITABLE = 2
  val UV_DISCONNECT = 4
  val UV_PRIORITIZED = 8

  def checkError(v: Int, label: String): Int =
    if v == 0 then
      println(s"$label returned $v")
      v
    else
      val error = fromCString(uv_err_name(v))
      val message = fromCString(uv_strerror(v))
      println(s"$label returned $v: $error: $message")
      v
