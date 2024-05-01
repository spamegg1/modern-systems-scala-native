package ch04
package nativePipeTwo

import scalanative.unsafe.{stackalloc, CQuote}
import scalanative.posix.unistd // getpid
import scalanative.libc.stdio

@main
def run(args: String*): Unit =
  println("about to fork")
  // I don't understand the 0 / 1. Are they supposed to be stdin / stdout? YUP (see below)
  val status = runTwoAndPipe(0, 1, Seq("/bin/ls", "."), Seq("/usr/bin/sort", "-r"))
  println(s"wait status ${status}")

// def badThrottle(commands: Seq[Seq[String]], maxParallel: Int) = ???
// def goodThrottle(commands: Seq[Seq[String]], maxParallel: Int) = ???

// order:
// Call pipe. It gives us two fresh file descriptors for two "ends" of the pipe to r/w.
// Call fork. Want to run 2 commands, so fork 2 times.
// In the child, call dup2. So child fd is interchangeable w/ stdin / stdout.
// In the child, call execve (or do other work). We can use our runCommand.
// In the parent, call close on the pipe.
// In the parent, call wait or waitpid, to wait for child.
def runTwoAndPipe(input: Int, output: Int, proc1: Seq[String], proc2: Seq[String]): Int =
  val pipeArray = stackalloc[Int](2) // pipe takes integer array of size exactly 2.
  val pipeRet = util.pipe(pipeArray) // call pipe! it will fill the array with 2 fds: r/w
  println(s"pipe() returned ${pipeRet}") // this is just status code of pipe. 0 on success
  val outputPipe = pipeArray(1) // this end of pipe is read-only, attached to stdout.
  val inputPipe = pipeArray(0) // this end of pipe is write-only, attached to stdin.

  //                                     -pipe-
  //     stdin -- command1 -- outputPipe ------ inputPipe -- command2 -- stdout
  // fd:   0       input     (call pipe)       (call pipe)    output        1
  // when we chain only 2 commands, input will be 0, output will be 1 (see main above).
  // when we chain > 2 commands, input / output will be other fds.
  // then, in proc1, we'll have to dup input to stdin, outputPipe to stdout.
  // then, in proc2, we'll have to dup inputPipe to stdin, output to stdout.
  val proc1Pid = doFork: () =>
    if input != 0 then // input will be 0 (until later when we do more than 2 commands)
      println(s"proc ${unistd.getpid()}: about to dup input $input to stdin")
      util.dup2(input, 0) // right, so I think 0 is stdin. This must be Linux convention.
      // Now, input's fd is interchangeable with stdin's fd.

    println(s"proc ${unistd.getpid()}: about to dup outputPipe $outputPipe to stdout")
    util.dup2(outputPipe, 1) // yep, and 1 is stdout. This must be Linux convention.
    stdio.printf(c"process %d about to runCommand\n", unistd.getpid()) // why printf?
    runCommand(proc1) // run first command (ls) in the newly forked child process.

  val proc2Pid = doFork: () =>
    println(s"proc ${unistd.getpid()}: about to dup inputPipe $inputPipe to stdin")
    util.dup2(inputPipe, 0) // now inputPipe fd is interchangeable w/ stdin
    if output != 1 then
      println(s"proc ${unistd.getpid()}: about to dup output $output to stdout")
      util.dup2(output, 1) // now output fd is interchangeable w/ stdout

    unistd.close(outputPipe) // why the duplicate below? Removing this hangs the program.
    stdio.printf(c"process %d about to runCommand\n", unistd.getpid()) // why printf?
    runCommand(proc2) // run second command (sort) in newly forked child process.

  unistd.close(input) // why isn't output closed? b/c we need it to output to Terminal?
  unistd.close(outputPipe) // why the duplicate above? Removing this hangs the program.
  unistd.close(inputPipe)

  val waitingFor = Seq(proc1Pid, proc2Pid) // we have two children to wait on.
  println(s"waiting for procs: $waitingFor")

  val r1 = util.waitpid(-1, null, 0) // -1 means: "wait for ANY child".
  println(s"proc $r1 returned") // first child finishes.

  val r2 = util.waitpid(-1, null, 0) // -1 means: "wait for ANY child".
  println(s"proc $r2 returned") // second child finishes.
  r2

// You can compile this with: (at the root directory of the project)
// scala-cli package . --main-class ch04.nativePipeTwo.nativePipeTwo
// ...
// Wrote /home/spam/Projects/modern-systems-scala-native/project, run it with
//   ./project
// Let's use it: it will sort the result of ls by piping it into sort:
// ./project
// about to fork
// pipe() returned 0
// forked, got pid 94127
// forked, got pid 94128
// in proc 94127
// waiting for procs: List(94127, 94128)
// proc 94127: about to dup outputPipe 4 to stdout
// in proc 94128
// proc 94128: about to dup inputPipe 3 to stdin
// process 94128 about to runCommand
// proc 94128: running cmd /usr/bin/sort with args List(-r)
// src
// project.scala
// project
// proc 94127: running cmd /bin/ls with args List(.)
// images
// README.md
// proc 94127 returned
// proc 94128 returned
// wait status 94128
//
// due to concurrency the output is printed all out of order!
// for some reason, README.md is always printed last! B/c it's capitalized?
// This is the normal output of /bin/ls . | /usr/bin/sort -r on the Terminal:
// $ /bin/ls . | /usr/bin/sort -r
// src
// README.md
// project.scala
// project
// images
