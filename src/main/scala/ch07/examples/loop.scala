package ch07
package examples

// This code is not meant to compile and run, it's conceptual pseudocode.
// I just made up some stuff to make it compile.

import collection.mutable.ListBuffer
import scalanative.libc.stdlib
import scalanative.unsafe.CFuncPtr1

object EventLoopExample extends ExecutionContextExecutor:
  import LibUV.*, LibUVConstants.*

  val loop = uv_default_loop()
  private val taskQueue = ListBuffer[Runnable]()
  // private val extensions = ListBuffer[LoopExtension]() // what is this? no idea.

  private def dispatch(handle: PrepareHandle): Unit =
    while taskQueue.nonEmpty do
      val runnable = taskQueue.remove(0)
      try runnable.run()
      catch case t: Throwable => reportFailure(t)
    if taskQueue.isEmpty then LibUV.uv_prepare_stop(handle)

  private val dispatch_cb = CFuncPtr1.fromScalaFunction(dispatch)

  val handle = stdlib.malloc(uv_handle_size(UV_PREPARE_T))
  uv_prepare_init(loop, handle)
  uv_prepare_start(handle, dispatch_cb)

  def execute(runnable: Runnable): Unit = taskQueue += runnable

  def reportFailure(t: Throwable): Unit = t.printStackTrace()

  def run(mode: Int = UV_RUN_DEFAULT): Unit =
    var continue = 1
    while continue != 0 do continue = uv_run(loop, mode)
