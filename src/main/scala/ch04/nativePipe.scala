package ch04
package nativePipe

import scalanative.unsafe.stackalloc
import scalanative.posix.unistd // getpid
import collection.mutable.ArrayBuffer

@main
def nativePipe(args: String*): Unit =
  val status = pipeMany(0, 1, Seq(Seq("/bin/ls", "."), Seq("/usr/bin/sort", "-r")))
  println(s"- wait returned ${status}")

def pipeMany(input: Int, output: Int, procs: Seq[Seq[String]]): Int =
  // to connect n processes, we need n-1 pipes.
  val pipeArray = stackalloc[Int](2 * (procs.size - 1)) // 2*(n-1)
  var inputFds = ArrayBuffer[Int](input) // input-o-o-o-...-o-o---o    = n
  var outputFds = ArrayBuffer[Int]()     //   o---o-o-o-...-o-o-output = n

  // pipeArray: in1 out1 in2 out2 ... in(n-1) out(n-1)
  for i <- 0 until procs.size - 1 do // create our array of pipes
    val arrayOffset = i * 2
    val pipeRet = util.pipe(pipeArray + arrayOffset) // put pipe fds in pipeArray
    outputFds += pipeArray(arrayOffset + 1) // then take those fds from pipeArray
    inputFds += pipeArray(arrayOffset) // and put them in inputFds and outputFds

  outputFds += output // output is at the end, the last one of the outputFds

  val procsWithFds = procs.lazyZip(inputFds).lazyZip(outputFds)

  val pids = // pids of forked child processes that we wait on.
    for (proc, inputFd, outputFd) <- procsWithFds
    yield doFork: () =>
      // close all pipes that this process won't be using. WHY?
      for p <- 0 until 2 * (procs.size - 1) do
        if pipeArray(p) != inputFd && pipeArray(p) != outputFd then
          unistd.close(pipeArray(p)) // WHY?

      // reassign STDIN if we aren't at the front of the pipeline
      if inputFd != input then
        unistd.close(unistd.STDIN_FILENO)
        util.dup2(inputFd, unistd.STDIN_FILENO) // inputFd becomes stdin

      // reassign STDOUT if we aren't at the end of the pipeline
      if outputFd != output then
        unistd.close(unistd.STDOUT_FILENO)
        util.dup2(outputFd, unistd.STDOUT_FILENO) // outputFd becomes stdout

      runCommand(proc)

  for p <- 0 until 2 * (procs.size - 1) do unistd.close(pipeArray(p))
  unistd.close(input) // output should not be closed. We need it to return stuff.

  var waitingFor = pids.toSet // pids of forked child processes we wait on

  while !waitingFor.isEmpty do
    val waitResult = util.waitpid(-1, null, 0) // wait for ANY child to finish.
    println(s"- waitpid returned ${waitResult}")
    waitingFor = waitingFor - waitResult

  0 // success code

// Compile with (in root directory):
// $ scala-cli package . --main-class ch04.nativePipe.nativePipe
// Run with (in root directory):
// $ ./project
// it's all printed out of order due to concurrency:
// forked, got pid 39819
// forked, got pid 39820
// in proc 39819
// in proc 39820
// proc 39820: running cmd /usr/bin/sort with args List(-r)
// - waitpid returned 39819
// src
// project.scala
// project
// proc 39819: running cmd /bin/ls with args List(.)
// images
// README.md
// - waitpid returned 39820
// - wait returned 0
