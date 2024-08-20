package ch07

import scalanative.unsafe.*

@link("uv")
@extern
object LibUV:
  type PipeHandle = Ptr[Byte] // mentioned in ch08
  type PollHandle = Ptr[Ptr[Byte]]
  type TCPHandle = Ptr[Byte] // different in ch09
  type TTYHandle = Ptr[Byte]
  type Loop = Ptr[Byte]
  type Buffer = CStruct2[Ptr[Byte], CSize]
  type WriteReq = Ptr[Ptr[Byte]]
  type ShutdownReq = Ptr[Ptr[Byte]]
  type Connection = Ptr[Byte]
  type ConnectionCB = CFuncPtr2[TCPHandle, Int, Unit]
  type AllocCB = CFuncPtr3[TCPHandle, CSize, Ptr[Buffer], Unit] // CSize = Usize unsigned
  type ReadCB = CFuncPtr3[TCPHandle, CSSize, Ptr[Buffer], Unit] // CSSize = Size signed
  type WriteCB = CFuncPtr2[WriteReq, Int, Unit]
  type ShutdownCB = CFuncPtr2[ShutdownReq, Int, Unit]
  type CloseCB = CFuncPtr1[TCPHandle, Unit]
  type PollCB = CFuncPtr3[PollHandle, Int, Int, Unit]
  type PrepareHandle = Ptr[Byte]
  type TimerHandle = Ptr[Byte]
  type PrepareCB = CFuncPtr1[PrepareHandle, Unit]
  type TimerCB = CFuncPtr1[TimerHandle, Unit]
  type FSReq = Ptr[Ptr[Byte]]
  type FSCB = CFuncPtr1[FSReq, Unit]

  def uv_prepare_init(loop: Loop, handle: PrepareHandle): Int = extern
  def uv_prepare_start(handle: PrepareHandle, cb: PrepareCB): Int = extern
  def uv_prepare_stop(handle: PrepareHandle): Unit = extern
  def uv_default_loop(): Loop = extern
  def uv_loop_size(): CSize = extern
  def uv_is_active(handle: Ptr[Byte]): Int = extern
  def uv_handle_size(h_type: Int): CSize = extern
  def uv_req_size(r_type: Int): CSize = extern
  def uv_tty_init(loop: Loop, handle: TTYHandle, fd: Int, readable: Int): Int = extern
  def uv_tcp_init(loop: Loop, tcp_handle: TCPHandle): Int = extern
  def uv_tcp_bind(tcp_handle: TCPHandle, address: Ptr[Byte], flags: Int): Int = extern
  def uv_ip4_addr(address: CString, port: Int, out_addr: Ptr[Byte]): Int = extern
  def uv_ip4_name(address: Ptr[Byte], s: CString, size: Int): Int = extern
  def uv_pipe_init(loop: Loop, handle: PipeHandle, ipc: Int): Int = extern
  def uv_pipe_open(handle: PipeHandle, fd: Int): Int = extern
  def uv_pipe_bind(handle: PipeHandle, socketName: CString): Int = extern
  def uv_poll_init_socket(loop: Loop, handle: PollHandle, socket: Ptr[Byte]): Int = extern
  def uv_poll_start(handle: PollHandle, events: Int, cb: PollCB): Int = extern
  def uv_poll_stop(handle: PollHandle): Int = extern
  def uv_timer_init(loop: Loop, handle: TimerHandle): Int = extern
  def uv_timer_start(handle: TimerHandle, cb: TimerCB, timeout: Long, repeat: Long): Int =
    extern
  def uv_timer_stop(handle: TimerHandle): Int = extern
  def uv_listen(handle: PipeHandle, backlog: Int, callback: ConnectionCB): Int = extern
  def uv_accept(server: PipeHandle, client: PipeHandle): Int = extern
  def uv_read_start(client: PipeHandle, allocCB: AllocCB, readCB: ReadCB): Int = extern
  def uv_write(
      writeReq: WriteReq,
      client: PipeHandle,
      bufs: Ptr[Buffer],
      numBufs: Int,
      writeCB: WriteCB
  ): Int = extern
  def uv_read_stop(client: PipeHandle): Int = extern
  def uv_shutdown(
      shutdownReq: ShutdownReq,
      client: PipeHandle,
      shutdownCB: ShutdownCB
  ): Int = extern
  def uv_close(handle: PipeHandle, closeCB: CloseCB): Unit = extern
  def uv_is_closing(handle: PipeHandle): Int = extern
  def uv_run(loop: Loop, runMode: Int): Int = extern
  def uv_strerror(err: Int): CString = extern
  def uv_err_name(err: Int): CString = extern
  def uv_fileno(handle: TTYHandle, fileno: Ptr[Int]): Int = extern
  def uv_handle_type_name(handle: TTYHandle): Int = extern
  def uv_guess_handle(fd: Int): Int = extern
  def uv_fs_open(
      loop: Loop,
      req: FSReq,
      path: CString,
      flags: Int,
      mode: Int,
      cb: FSCB
  ): Int = extern
  def uv_fs_read(
      loop: Loop,
      req: FSReq,
      fd: Int,
      bufs: Ptr[Buffer],
      numBufs: Int,
      offset: Long,
      fsCB: FSCB
  ): Int = extern
  def uv_fs_write(
      loop: Loop,
      req: FSReq,
      fd: Int,
      bufs: Ptr[Buffer],
      numBufs: Int,
      offset: Long,
      fsCB: FSCB
  ): Int = extern
  def uv_fs_close(loop: Loop, req: FSReq, fd: Int, fsCB: FSCB): Int = extern
  def uv_req_cleanup(req: FSReq): Unit = extern
  def uv_fs_get_result(req: FSReq): Int = extern
  def uv_fs_get_ptr(req: FSReq): Ptr[Byte] = extern
