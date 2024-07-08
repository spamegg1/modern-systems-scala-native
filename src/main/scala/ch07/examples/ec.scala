package ch07
package examples

// This code is not meant to compile and run, it's conceptual pseudocode.
// I just made up some stuff to make it compile.

trait ExecutionContext:
  /** Runs a block of code on this execution context. */
  def execute(runnable: Runnable): Unit

  /** Reports that an asynchronous computation failed. */
  def reportFailure(t: Throwable): Unit

trait ExecutionContextExecutor // made it up!

object ExecutionContext:
  import collection.mutable.ListBuffer

  def global: ExecutionContextExecutor = QueueExecutionContext

  private object QueueExecutionContext extends ExecutionContextExecutor:
    def execute(runnable: Runnable): Unit = queue += runnable
    def reportFailure(t: Throwable): Unit = t.printStackTrace()

  private val queue: ListBuffer[Runnable] = new ListBuffer

  private def loop(): Unit =
    while queue.nonEmpty do
      val runnable = queue.remove(0)
      try runnable.run()
      catch case t: Throwable => QueueExecutionContext.reportFailure(t)
