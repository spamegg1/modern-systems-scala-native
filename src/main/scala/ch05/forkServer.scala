package ch05.forkServer

import scalanative.posix.unistd
import scalanative.posix.sys.wait.WNOHANG

import ch04.common.util.{fork, waitpid}
import ch05.common.handleConnection

def forkAndHandle(connectionFd: Int, maxSize: Int = 1024): Unit =
  val pid = fork()
  if pid != 0 then // In parent process
    println(s"forked pid $pid to handle connection")
    unistd.close(connectionFd)
    cleanupChildren
  else // In child process
    println(s"fork returned $pid, in child process")
    handleConnection(connectionFd, maxSize)
    sys.exit() // this is scala.sys, not posix.sys

@annotation.tailrec
def cleanupChildren: Unit =
  val childPid = waitpid(-1, null, WNOHANG) // -1, NULL, WNOHANG
  if childPid <= 0 then () else cleanupChildren
