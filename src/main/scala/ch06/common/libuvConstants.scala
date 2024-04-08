package ch06

object LibUVConstants:
  import scalanative.unsafe.fromCString
  import LibUV.{uv_err_name, uv_strerror}

  // uv_run_mode
  val UV_RUN_DEFAULT = 0
  val UV_RUN_ONCE = 1
  val UV_RUN_NOWAIT = 2

  // UV_HANDLE_T
  val UV_PIPE_T = 7 // Pipes
  val UV_POLL_T = 8 // Polling external sockets
  val UV_PREPARE_T = 9 // Runs every loop iteration
  val UV_PROCESS_T = 10 // Subprocess
  val UV_TCP_T = 12 // TCP sockets
  val UV_TIMER_T = 13 // Timer
  val UV_TTY_T = 14 // Terminal emulator
  val UV_UDP_T = 15 // UDP sockets

  // UV_REQ_T
  val UV_WRITE_REQ_T = 3
  val UV_SHUTDOWN_REQ_T = 4

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
