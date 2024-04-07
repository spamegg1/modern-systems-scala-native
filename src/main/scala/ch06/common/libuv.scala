package ch06.common

import scalanative.unsafe.{extern, link, Ptr, CSize, CSSize, CString}
import scalanative.unsafe.{CStruct2, CFuncPtr1, CFuncPtr2, CFuncPtr3}

@link("uv")
@extern
object LibUV:
  type TimerHandle = Ptr[Byte]
  type PipeHandle = Ptr[Ptr[Byte]]
  type Loop = Ptr[Byte]
  type TCPHandle = Ptr[Ptr[Byte]]
  type WriteReq = Ptr[Ptr[Byte]]
  type ShutdownReq = Ptr[Ptr[Byte]]
  type Buffer = CStruct2[Ptr[Byte], CSize]

  type TimerCB = CFuncPtr1[TimerHandle, Unit]
  type ConnectionCB = CFuncPtr2[TCPHandle, Int, Unit] // can't use!
  type AllocCB = CFuncPtr3[TCPHandle, CSize, Ptr[Buffer], Unit]
  type ReadCB = CFuncPtr3[TCPHandle, CSSize, Ptr[Buffer], Unit]
  type WriteCB = CFuncPtr2[WriteReq, Int, Unit]
  type ShutdownCB = CFuncPtr2[ShutdownReq, Int, Unit]
  type CloseCB = CFuncPtr1[TCPHandle, Unit]

  def uv_default_loop(): Loop = extern
  def uv_loop_size(): CSize = extern
  def uv_is_active(handle: Ptr[Byte]): Int = extern
  def uv_handle_size(h_type: Int): CSize = extern
  def uv_req_size(r_type: Int): CSize = extern
  def uv_timer_init(loop: Loop, handle: TimerHandle): Int = extern
  def uv_timer_start(handle: TimerHandle, cb: TimerCB, timeout: Long, repeat: Long): Int =
    extern
  def uv_timer_stop(handle: TimerHandle): Int = extern
  def uv_run(loop: Loop, runMode: Int): Int = extern
  def uv_strerror(err: Int): CString = extern
  def uv_err_name(err: Int): CString = extern
  def uv_tcp_init(loop: Loop, tcp_handle: TCPHandle): Int = extern
  def uv_tcp_bind(tcp_handle: TCPHandle, address: Ptr[Byte], flags: Int): Int = extern
  def uv_listen(
      stream_handle: TCPHandle,
      backlog: Int,
      uv_connection_cb: ConnectionCB
  ): Int = extern
  def uv_accept(server: TCPHandle, client: TCPHandle): Int = extern
  def uv_read_start(client: TCPHandle, allocCB: AllocCB, readCB: ReadCB): Int = extern
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
  def uv_ip4_addr(address: CString, port: Int, out_addr: Ptr[Byte]): Int = extern
  def uv_ip4_name(address: Ptr[Byte], s: CString, size: Int): Int = extern
