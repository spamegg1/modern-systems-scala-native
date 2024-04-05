package ch04.nativePipe

import scalanative.unsafe.stackalloc
import scalanative.posix.unistd // getpid
import collection.mutable.ArrayBuffer
import ch04.common
import common.util

@main
def nativePipe(args: String*): Unit =
  val status = pipeMany(0, 1, Seq(Seq("/bin/ls", "."), Seq("/usr/bin/sort", "-r")))
  println(s"- wait returned ${status}")

// def badThrottle(commands: Seq[Seq[String]], maxParallel: Int) = ??? // TODO
// def goodThrottle(commands: Seq[Seq[String]], maxParallel: Int) = ??? // TODO

def pipeMany(input: Int, output: Int, procs: Seq[Seq[String]]): Int =
  val pipeArray = stackalloc[Int](2 * (procs.size - 1))
  var inputFds = ArrayBuffer[Int](input)
  var outputFds = ArrayBuffer[Int]()

  for i <- 0 until procs.size - 1 do // create our array of pipes
    val arrayOffset = i * 2
    val pipeRet = util.pipe(pipeArray + arrayOffset)
    outputFds += pipeArray(arrayOffset + 1)
    inputFds += pipeArray(arrayOffset)

  outputFds += output

  // val procsWithFds = (procs, inputFds, outputFds).zipped
  val procsWithFds = procs.lazyZip(inputFds).lazyZip(outputFds)
  val pids =
    for (proc, inputFd, outputFd) <- procsWithFds
    yield common.doFork: () =>
      // close all pipes that this process won't be using.
      for p <- 0 until 2 * (procs.size - 1) do
        if pipeArray(p) != inputFd && pipeArray(p) != outputFd then
          unistd.close(pipeArray(p))

      // reassign STDIN if we aren't at the front of the pipeline
      if inputFd != input then
        unistd.close(unistd.STDIN_FILENO)
        util.dup2(inputFd, unistd.STDIN_FILENO)

      // reassign STDOUT if we aren't at the end of the pipeline
      if outputFd != output then
        unistd.close(unistd.STDOUT_FILENO)
        util.dup2(outputFd, unistd.STDOUT_FILENO)

      common.runCommand(proc)

  for i <- 0 until 2 * (procs.size - 1) do unistd.close(pipeArray(i))
  unistd.close(input)

  var waitingFor = pids.toSet
  while !waitingFor.isEmpty do
    val waitResult = util.waitpid(-1, null, 0)
    println(s"- waitpid returned ${waitResult}")
    waitingFor = waitingFor - waitResult
  0
