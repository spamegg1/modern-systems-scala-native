/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
trait ExecutionContext {
  
  /** Runs a block of code on this execution context. */
  def execute(runnable: Runnable): Unit
  
  /** Reports that an asynchronous computation failed. */
  def reportFailure(t: Throwable): Unit

}

object ExecutionContext {
  def global: ExecutionContextExecutor = QueueExecutionContext

  private object QueueExecutionContext extends ExecutionContextExecutor {
    def execute(runnable: Runnable): Unit = queue += runnable
    def reportFailure(t: Throwable): Unit = t.printStackTrace()
  }

  private val queue: ListBuffer[Runnable] = new ListBuffer

  private def loop(): Unit = {
    while (queue.nonEmpty) {
      val runnable = queue.remove(0)
      try {
        runnable.run()
      } catch {
        case t: Throwable =>
          QueueExecutionContext.reportFailure(t)
      }
    }
  }
}
