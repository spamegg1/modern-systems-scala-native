package ch04.nativePipeTwo

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.*
import scalanative.libc.*
import scalanative.posix.unistd

import ch04.nativeFork.{Command, doFork, await, makeStringArray, runCommand, doAndAwait}
import ch04.nativeFork.{util, runOneAtATime, awaitAny, awaitAll, runSimultaneously}

@main
def nativePipeTwo(args: String*): Unit =
  println("about to fork")
  val status = runTwoAndPipe(0, 1, Seq("/bin/ls", "."), Seq("/usr/bin/sort", "-r"))
  println(s"wait status ${status}")

// def badThrottle(commands: Seq[Seq[String]], maxParallel: Int) = ???
// def goodThrottle(commands: Seq[Seq[String]], maxParallel: Int) = ???

def runTwoAndPipe(input: Int, output: Int, proc1: Seq[String], proc2: Seq[String]): Int =
  val pipeArray = stackalloc[Int](2)
  val pipeRet = util.pipe(pipeArray)
  println(s"pipe() returned ${pipeRet}")
  val outputPipe = pipeArray(1)
  val inputPipe = pipeArray(0)

  val proc1Pid = doFork: () =>
    if input != 0 then
      println(s"proc ${unistd.getpid()}: about to dup ${input} to stdin")
      util.dup2(input, 0)

    println(s"proc 1 about to dup ${outputPipe} to stdout")
    util.dup2(outputPipe, 1)
    stdio.printf(c"process %d about to runCommand\n", unistd.getpid())
    runCommand(proc1)

  val proc2Pid = doFork: () =>
    println(s"proc ${unistd.getpid()}: about to dup")
    util.dup2(inputPipe, 0)
    if output != 1 then util.dup2(output, 1)

    unistd.close(outputPipe)
    stdio.printf(c"process %d about to runCommand\n", unistd.getpid())
    runCommand(proc2)

  unistd.close(input)
  unistd.close(outputPipe)
  unistd.close(inputPipe)

  val waitingFor = Seq(proc1Pid, proc2Pid)
  println(s"waiting for procs: ${waitingFor}")

  val r1 = util.waitpid(-1, null, 0)
  println(s"proc $r1 returned")

  val r2 = util.waitpid(-1, null, 0)
  println(s"proc $r2 returned")
  r2
