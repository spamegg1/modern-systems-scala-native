package ch04.nativePipe

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.*
import scalanative.libc.*
import scalanative.posix.unistd
import collection.mutable
import util.*

case class Command(path: String, args: String, env: Map[String, String])

// @main
def nativePipe(args: String*): Unit =
  val status = pipeMany(0, 1, Seq(Seq("/bin/ls", "."), Seq("/usr/bin/sort", "-r")))
  println(s"- wait returned ${status}")

def doFork(task: Function0[Int]): Int =
  val pid = fork()
  if pid > 0 then pid
  else
    val res = task.apply()
    stdlib.exit(res)
    res

def await(pid: Int): Int =
  val status = stackalloc[Int]()
  waitpid(pid, status, 0)
  val statusCode = !status
  if statusCode != 0 then throw Exception(s"Child process returned error $statusCode")
  !status

def doAndAwait(task: Function0[Int]): Int =
  val pid = doFork(task)
  await(pid)

def runCommand(args: Seq[String], env: Map[String, String] = Map.empty): Int =
  if args.size == 0 then throw Exception("bad arguments of length 0")

  Zone { // implicit z => // 0.5
    println(s"- proc ${unistd.getpid()}: running command ${args.head} with args ${args}")
    val fname = toCString(args.head)
    val argArray = stringSeqToStringArray(args)
    val envStrings = env.map((k, v) => s"$k=$v")
    val envArray = stringSeqToStringArray(envStrings.toSeq)

    val r = execve(fname, argArray, envArray)
    if r != 0 then
      val err = errno.errno
      stdio.printf(c"error: %d %d\n", err, string.strerror(err))
      throw Exception(s"bad execve: returned $r")
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
      val destString = stdlib.malloc(stringLen) // 0.5
      string.strncpy(destString, stringPtr, arg.size.toUSize) // 0.5
      destString(stringLen) = 0.toByte
      destArray(i) = destString
  }
  // destArray(count) = null
  destArray

// TODO: TEST
def runOneAtATime(commands: Seq[Seq[String]]) =
  for command <- commands do doAndAwait(() => runCommand(command))

// TODO: TEST
def runSimultaneously(commands: Seq[Seq[String]]) =
  val pids = for command <- commands yield doFork(() => runCommand(command))
  for pid <- pids do await(pid)

def awaitAny(pids: Set[Int]): Set[Int] =
  val status = stackalloc[Int](sizeof[Int])
  var running = pids
  !status = 0
  val finished = waitpid(-1, status, 0)

  if running.contains(finished) then
    val statusCode = !status
    if statusCode != 0 then throw Exception(s"Child process returned error $statusCode")
    else pids - finished
  else throw Exception(s"error: reaped process ${finished}, expected one of $pids")

def awaitAll(pids: Set[Int]): Unit =
  var running = pids
  while running.nonEmpty do
    println(s"- waiting for $running")
    running = awaitAny(running)
  println("- Done!")

def badThrottle(commands: Seq[Seq[String]], maxParallel: Int) = ??? // TODO
def goodThrottle(commands: Seq[Seq[String]], maxParallel: Int) = ??? // TODO

def pipeMany(input: Int, output: Int, procs: Seq[Seq[String]]): Int =
  val pipeArray = stackalloc[Int](2 * (procs.size - 1))
  var inputFds = mutable.ArrayBuffer[Int](input)
  var outputFds = mutable.ArrayBuffer[Int]()

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
    yield doFork: () =>
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

      runCommand(proc)

  for i <- 0 until 2 * (procs.size - 1) do unistd.close(pipeArray(i))

  unistd.close(input)

  var waitingFor = pids.toSet
  while !waitingFor.isEmpty do
    val waitResult = waitpid(-1, null, 0)
    println(s"- waitpid returned ${waitResult}")
    waitingFor = waitingFor - waitResult
  0

@extern
object util:
  def execve(filename: CString, args: Ptr[CString], env: Ptr[CString]): Int = extern
  def execvp(path: CString, args: Ptr[CString]): Int = extern
  def fork(): Int = extern
  def getpid(): Int = extern
  def waitpid(pid: Int, status: Ptr[Int], options: Int): Int = extern
  def strerror(errno: Int): CString = extern
  def pipe(pipes: Ptr[Int]): Int = extern
  def dup2(oldfd: Int, newfd: Int): Int = extern
