package ch07

import scalanative.unsafe.fromCString

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
  val UV_SHUTDOWN_REQ_T = 4
  val UV_FS_REQ_T = 6

  val UV_READABLE = 1
  val UV_WRITABLE = 2
  val UV_DISCONNECT = 4
  val UV_PRIORITIZED = 8

  val O_RDWR = 2
  val O_CREAT = sys.props("os.name") match
    case "Mac OS X" => 512
    case _          => 64

  val default_permissions = 420 // octal 0644

  def checkError(v: Int, label: String): Int =
    if v == 0 then
      println(s"$label returned $v")
      v
    else
      val error = fromCString(uv_err_name(v))
      val message = fromCString(uv_strerror(v))
      println(s"$label returned $v: $error: $message")
      v
