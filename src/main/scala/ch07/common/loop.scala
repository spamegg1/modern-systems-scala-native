package ch07

import scalanative.unsafe.*
import scalanative.libc.stdlib
import collection.mutable.ListBuffer
import concurrent.{ExecutionContext, ExecutionContextExecutor, Future, Promise}
import util.{Try, Success}

// implementing an ExecutionContext using libuv's prepare handle
// runs every future it can, completes and allows prg to exit when no more work left.
object EventLoop extends ExecutionContextExecutor: // is an ExecutionContext too.
  import LibUV.*, LibUVConstants.*

  val loop = uv_default_loop() // prepare handle is fired on every iter of loop before i/o
  private val taskQueue = ListBuffer[Runnable]() // callback pulls from this queue.

  private val handle: PrepareHandle = stdlib.malloc(uv_handle_size(UV_PREPARE_T))
  checkError(uv_prepare_init(loop, handle), "uv_prepare_init") // init loop with handle

  // This is for uv_prepare_start. Pulls work from queue every time it's invoked.
  val prepareCallback = CFuncPtr1.fromScalaFunction[PrepareHandle, Unit]: // Ptr[Byte]
    (handle: PrepareHandle) =>
      while taskQueue.nonEmpty do
        val runnable: Runnable = taskQueue.remove(0)
        try runnable.run()
        catch case t: Throwable => reportFailure(t)

      if taskQueue.isEmpty then
        println("stopping dispatcher")
        uv_prepare_stop(handle) // manually stop the handle.

  // This is part of ExecutionContext interface we need to implement.
  def execute(runnable: Runnable): Unit =
    taskQueue += runnable // we add it to queue, then callback is fired and executes task.

    // we manually "start" the loop many times, but this is harmless.
    checkError(uv_prepare_start(handle, prepareCallback), "uv_prepare_start")

  // This is part of ExecutionContext interface we need to implement.
  def reportFailure(t: Throwable): Unit =
    println(s"Future failed with Throwable $t:")
    t.printStackTrace()

  // uv_run completes and exits when there is no more work left.
  def run(mode: Int = UV_RUN_DEFAULT): Unit = // wrapper around uv_run
    var continue = 1
    while continue != 0 do
      continue = uv_run(loop, mode)
      println(s"uv_run returned $continue")

  // built-in Scala EC will immediately run our event loop when main completes.
  private val bootstrapFuture = Future(run())(ExecutionContext.global) // ???
