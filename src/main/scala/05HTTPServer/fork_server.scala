/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
def fork_and_handle(conn_fd:Int, max_size:Int = 1024): Unit = {
    val pid = fork()
    if (pid != 0) {
        // In parent process
        println("forked pid $pid to handle connection")
        close(conn_fd)
        cleanup_children()
        return
    } else {
        // In child process
        println("fork returned $pid, in child process")
        handle_connection(conn_fd, max_size)
        sys.exit()
    }
}

def cleanup_children(): Unit = {
  val child_pid = waitpid(-1, NULL, WNOHANG)
  if (child_pid <= 0) {
    return
  } else {
    cleanup_children()
  }
}
