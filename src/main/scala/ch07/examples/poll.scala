package ch07
package examples

import scalanative.unsafe.{extern, link, Ptr, CFuncPtr3}

// This code is not meant to compile and run, it's conceptual pseudocode.
// I just made up some stuff to make it compile.

@link("uv")
@extern
object Poll:
  type Loop = ExecutionContext // made it up!
  type PollHandle = Ptr[Ptr[Byte]]
  type PollCB = CFuncPtr3[PollHandle, Int, Int, Unit]

  def uv_poll_init_socket(loop: Loop, handle: PollHandle, socket: Ptr[Byte]): Int = extern
  def uv_poll_start(handle: PollHandle, events: Int, cb: PollCB): Int = extern
  def uv_poll_stop(handle: PollHandle): Int = extern
