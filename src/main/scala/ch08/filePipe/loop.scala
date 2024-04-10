package ch08
package filePipe

import scalanative.unsafe.*
import scalanative.libc.stdlib
import collection.mutable.ListBuffer
import concurrent.{ExecutionContext, ExecutionContextExecutor, Future, Promise}
import scala.util.{Try, Success}

object EventLoop extends ExecutionContextExecutor:
  import ch07.LibUV.*, ch07.LibUVConstants.*

  val loop = uv_default_loop()
  private val taskQueue = ListBuffer[Runnable]()
  private val handle = stdlib.malloc(uv_handle_size(UV_PREPARE_T))
  checkError(uv_prepare_init(loop, handle), "uv_prepare_init")

  val prepareCallback = CFuncPtr1.fromScalaFunction[PrepareHandle, Unit]:
    (handle: PrepareHandle) =>
      while taskQueue.nonEmpty do
        val runnable = taskQueue.remove(0)
        try runnable.run()
        catch case t: Throwable => reportFailure(t)

      if taskQueue.isEmpty then
        println("stopping dispatcher")
        uv_prepare_stop(handle)

  def execute(runnable: Runnable): Unit =
    taskQueue += runnable
    checkError(uv_prepare_start(handle, prepareCallback), "uv_prepare_start")

  def reportFailure(t: Throwable): Unit =
    println(s"Future failed with Throwable $t:")
    t.printStackTrace()

  def run(mode: Int = UV_RUN_DEFAULT): Unit =
    var continue = 1
    while (continue != 0) do
      continue = uv_run(loop, mode)
      println(s"uv_run returned $continue")

  private val bootstrapFuture = Future(run())(ExecutionContext.global)
