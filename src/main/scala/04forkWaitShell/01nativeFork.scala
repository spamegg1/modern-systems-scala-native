package `03nativeFork`

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.*
import scalanative.libc.*
// import scalanative.native.*
import scalanative.posix.unistd

case class Command(path: String, args: String, env: Map[String, String])

import util.*

// @main
def nativeFork(args: String*): Unit =
  if args.size == 0 then
    println("bye")
    stdlib.exit(1)

  println("about to fork")

  val status = doAndAwait(() =>
    println(s"in child, about to exec command: ${args.toSeq}")
    runCommand(args)
  )
  println(s"wait status ${status}")

def doFork(task: Function0[Int]): Int =
  val pid = fork()
  if pid > 0 then pid
  else
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
    val fname = toCString(args.head)
    val argArray = makeStringArray(args)
    val envStrings = env.map { case (k, v) => s"$k=$v" }
    val envArray = makeStringArray(envStrings.toSeq)

    val r = execve(fname, argArray, envArray)
    if r != 0 then
      val err = errno.errno
      stdio.printf(c"error: %d %d\n", err, string.strerror(err))
      throw new Exception(s"bad execve: returned $r")
  }
  ??? // This will never be reached.

def makeStringArray(args: Seq[String]): Ptr[CString] =
  val pid = unistd.getpid()
  val size = sizeof[Ptr[CString]] * args.size.toUSize + 1.toUSize // 0.5
  val destArray = stdlib.malloc(size).asInstanceOf[Ptr[CString]]
  val count = args.size

  Zone { // implicit z => // 0.5
    for (arg, i) <- args.zipWithIndex
    do
      val stringPtr = toCString(arg)
      val stringLen = string.strlen(stringPtr)
      val destString = stdlib.malloc(stringLen).asInstanceOf[Ptr[Byte]]
      string.strncpy(destString, stringPtr, arg.size.toUSize) // 0.5
      // destString(stringLen) = 0
      destArray(i) = destString
      // ()
    ()
  }
  destArray(count) = null
  // for j <- 0 to count do {}
  destArray

def runOneAtATime(commands: Seq[Seq[String]]) =
  for command <- commands
  do doAndAwait(() => runCommand(command))

def runSimultaneously(commands: Seq[Seq[String]]) =
  val pids =
    for command <- commands
    yield doFork { () => runCommand(command) }

  for pid <- pids do await(pid)

def awaitAny(pids: Set[Int]): Set[Int] =
  val status = stackalloc[Int](sizeof[Int])
  var running = pids
  !status = 0

  val finished = waitpid(-1, status, 0)
  if running.contains(finished) then
    val statusCode = !status
    if statusCode != 0 then
      throw new Exception(s"Child process returned error $statusCode")
    else return pids - finished
  else throw new Exception(s"""error: reaped process ${finished},
                        expected one of $pids""")

def awaitAll(pids: Set[Int]): Unit =
  var running = pids
  while running.nonEmpty
  do
    println(s"waiting for $running")
    running = awaitAny(running)

  println("Done!")

// TODO
def badThrottle(commands: Seq[Seq[String]], maxParallel: Int) = ???

// TODO
def goodThrottle(commands: Seq[Seq[String]], maxParallel: Int) = ???

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
