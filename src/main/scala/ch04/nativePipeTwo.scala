package ch04
package nativePipeTwo

import scalanative.unsafe.{stackalloc, CQuote}
import scalanative.posix.unistd // getpid
import scalanative.libc.stdio

@main
def nativePipeTwo(args: String*): Unit =
  println("about to fork")
  val status = runTwoAndPipe(0, 1, Seq("/bin/ls", "."), Seq("/usr/bin/sort", "-r"))
  println(s"wait status ${status}")

// def badThrottle(commands: Seq[Seq[String]], maxParallel: Int) = ???
// def goodThrottle(commands: Seq[Seq[String]], maxParallel: Int) = ???

// order:
// Call pipe.
// Call fork.
// In the child, call dup2.
// In the child, call execve (or do other work).
// In the parent, call close on the pipe.
// In the parent, call wait or waitpid.
def runTwoAndPipe(input: Int, output: Int, proc1: Seq[String], proc2: Seq[String]): Int =
  val pipeArray = stackalloc[Int](2) // pipe takes integer array of size exactly 2.
  val pipeRet = util.pipe(pipeArray) // call pipe! it will fill the array with 2 fds: r/w
  println(s"pipe() returned ${pipeRet}") // this is just status code of pipe. 0 on success
  val outputPipe = pipeArray(1) // we need to write data to this end of pipe.
  val inputPipe = pipeArray(0) // data written needs to be read from this end of pipe.

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

// You can compile this with: (at the root directory of the project)
// scala-cli package . --main-class ch04.nativePipeTwo.nativePipeTwo
// ...
// Wrote /home/spam/Projects/modern-systems-scala-native/project, run it with
//   ./project
// Let's use it: it will sort the result of ls by piping it into sort:
// ./project
