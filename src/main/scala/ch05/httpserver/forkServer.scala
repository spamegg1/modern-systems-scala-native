package ch05
package forkServer

import scalanative.posix.unistd
import scalanative.posix.sys.wait.WNOHANG

import ch04.util.{fork, waitpid}

def forkAndHandle(connectionFd: Int, maxSize: Int = 1024): Unit =
  val pid = fork() // fork returns twice: to parent and to child.
  if pid != 0 then // pid = child pid, so we are in parent process
    println(s"forked pid $pid to handle connection")
    unistd.close(connectionFd) // this fd is copied to child. Close this one.
    cleanupChildren // just wait until children are finished.
  else // pid = 0, so we are in child process
    println(s"fork returned $pid, in child process")
    handleConnection(connectionFd, maxSize)
    sys.exit() // this is scala.sys, not posix.sys

@annotation.tailrec
def cleanupChildren: Unit = // look at man waitpid
  // -1: wait for any children. null: we don't care about exit status of child.
  // WNOHANG: no hanging (blocking), return immediately if no child exited.
  val childPid = waitpid(-1, null, WNOHANG) // null was NULL
  if childPid <= 0 then () // no child changed status, so we must be done.
  else cleanupChildren // waitpid returned pid of a child that exited.
