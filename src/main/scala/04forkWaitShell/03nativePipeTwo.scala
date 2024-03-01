package `04nativePipeTwo`

import scalanative.unsigned.UnsignedRichInt
import scala.scalanative.unsafe.*
import scala.scalanative.libc.*
// import scalanative.native.*
import scala.scalanative.posix.unistd

case class Command(path: String, args: String, env: Map[String, String])

import util.*

// @main
def nativePipeTwo(args: String*): Unit =
  println("about to fork")
  val status =
    runTwoAndPipe(0, 1, Seq("/bin/ls", "."), Seq("/usr/bin/sort", "-r"))
  println(s"wait status ${status}")

def doFork(task: Function0[Int]): Int =
  val pid = fork()
  if pid > 0 then
    println(s"forked, got pid ${pid}")
    pid
  else
    println(s"in proc ${unistd.getpid()}")
    val res = task.apply()
    stdlib.exit(res)
    res

def await(pid: Int): Int =
  val status = stackalloc[Int](sizeof[Int])
  waitpid(pid, status, 0)
  val statusCode = !status
  if statusCode != 0 then throw new Exception(s"Child process returned error $statusCode")
  !status

def doAndAwait(task: Function0[Int]): Int =
  val pid = doFork(task)
  await(pid)

def runCommand(
    args: Seq[String],
    env: Map[String, String] = Map.empty
): Int =
  if args.size == 0 then throw new Exception("bad arguments of length 0")

  Zone { // implicit z => // 0.5
    println(
      s"proc ${unistd.getpid()}: running command ${args.head} with args ${args}"
    )
    val fname = toCString(args.head)
    val argArray = stringSeqToStringArray(args)
    val envStrings = env.map { case (k, v) => s"$k=$v" }
    val envArray = stringSeqToStringArray(envStrings.toSeq)

    val r = execve(fname, argArray, envArray)
    if r != 0 then
      val err = errno.errno
      stdio.printf(c"error: %d %d\n", err, string.strerror(err))
      throw new Exception(s"bad execve: returned $r")
  }
  ??? // This will never be reached.

def stringSeqToStringArray(args: Seq[String]): Ptr[CString] =
  val pid = unistd.getpid()
  val destArray = stdlib
    .malloc(sizeof[Ptr[CString]] * args.size.toUSize) // 0.5
    .asInstanceOf[Ptr[CString]]
  val count = args.size
  Zone { // implicit z => // 0.5
    for (arg, i) <- args.zipWithIndex
    do
      val stringPtr = toCString(arg)
      val stringLen = string.strlen(stringPtr)
      val destStr = stdlib.malloc(stringLen).asInstanceOf[Ptr[Byte]]
      string.strncpy(destStr, stringPtr, arg.size.toUSize) // 0.5
      // destStr(stringLen) = 0
      destArray(i) = destStr
    ()
  }
  // destArray(count) = null
  // for j <- 0 to count do {}
  destArray

def runOneAtATime(commands: Seq[Seq[String]]) =
  for (command <- commands)
  do doAndAwait(() => runCommand(command))

def runSimultaneously(commands: Seq[Seq[String]]) =
  val pids =
    for command <- commands
    yield doFork(() => runCommand(command))
  for pid <- pids do await(pid)

def awaitAny(pids: Set[Int]): Set[Int] =
  val status = stackalloc[Int](sizeof[Int])
  var running = pids
  !status = 0

  val finished = waitpid(-1, status, 0)
  if running.contains(finished) then
    val statusCode = !status // TODO: check_status(status)
    if statusCode != 0 then
      throw new Exception(s"Child process returned error $statusCode")
    else return pids - finished
  else
    throw new Exception(
      s"error: reaped process ${finished}, expected one of $pids"
    )

def awaitAll(pids: Set[Int]): Unit =
  var running = pids
  while running.nonEmpty
  do
    println(s"waiting for $running")
    running = awaitAny(running)
  println("Done!")

def badThrottle(commands: Seq[Seq[String]], maxParallel: Int) = ???

def goodThrottle(commands: Seq[Seq[String]], maxParallel: Int) = ???

def runTwoAndPipe(
    input: Int,
    output: Int,
    proc1: Seq[String],
    proc2: Seq[String]
): Int =
  val pipeArray = stackalloc[Int](2)
  val pipeRet = util.pipe(pipeArray)
  println(s"pipe() returned ${pipeRet}")
  val outputPipe = pipeArray(1)
  val inputPipe = pipeArray(0)

  val proc1Pid = doFork { () =>
    if input != 0 then
      println(s"proc ${unistd.getpid()}: about to dup ${input} to stdin")
      util.dup2(input, 0)

    println(s"proc 1 about to dup ${outputPipe} to stdout")
    util.dup2(outputPipe, 1)
    stdio.printf(c"process %d about to runCommand\n", unistd.getpid())
    runCommand(proc1)
  }

  val proc2Pid = doFork { () =>
    println(s"proc ${unistd.getpid()}: about to dup")
    util.dup2(inputPipe, 0)
    if output != 1 then util.dup2(output, 1)

    unistd.close(outputPipe)
    stdio.printf(c"process %d about to runCommand\n", unistd.getpid())
    runCommand(proc2)
  }

  unistd.close(input)
  unistd.close(outputPipe)
  unistd.close(inputPipe)

  val waitingFor = Seq(proc1Pid, proc2Pid)
  println(s"waiting for procs: ${waitingFor}")

  val r1 = waitpid(-1, null, 0)
  println(s"proc $r1 returned")

  val r2 = waitpid(-1, null, 0)
  println(s"proc $r2 returned")
  r2

@extern
object util:
  def execve(filename: CString, args: Ptr[CString], env: Ptr[CString]): Int =
    extern
  def execvp(path: CString, args: Ptr[CString]): Int = extern
  def fork(): Int = extern
  def getpid(): Int = extern
  def waitpid(pid: Int, status: Ptr[Int], options: Int): Int = extern
  def strerror(errno: Int): CString = extern
  def pipe(pipes: Ptr[Int]): Int = extern
  def dup2(oldfd: Int, newfd: Int): Int = extern
