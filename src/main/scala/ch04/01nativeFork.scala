package ch04.nativeFork

import scalanative.unsigned.UnsignedRichInt // .toUSize
import scalanative.unsafe.{CQuote, toCString, Ptr, CString}
import scalanative.unsafe.{Zone, stackalloc, sizeof, extern}
import scalanative.libc.{stdlib, string, stdio, errno}
import scalanative.posix.unistd // getpid
import scala.scalanative.unsigned.USize

// This is a "conceptual" Scala version of execve
case class Command(path: String, args: String, env: Map[String, String])

@main
def nativeFork(args: String*): Unit =
  if args.size == 0 then
    println("bye")
    stdlib.exit(1)

  println("about to fork")

  val status = doAndAwait: () =>
    println(s"in child, about to exec command: ${args.toSeq}")
    runCommand(args)

  println(s"wait status ${status}")

def doFork(task: Function0[Int]): Int =
  val pid = util.fork()
  if pid > 0 then pid
  else
    val res = task.apply()
    stdlib.exit(res)
    res

def await(pid: Int): Int =
  val status = stackalloc[Int]()
  util.waitpid(pid, status, 0)
  val statusCode = !status
  if statusCode != 0 then throw Exception(s"Child process returned error $statusCode")
  !status

def doAndAwait(task: Function0[Int]): Int =
  val pid = doFork(task)
  await(pid)

def runCommand(args: Seq[String], env: Map[String, String] = Map.empty): Int =
  if args.size == 0 then throw Exception("bad arguments of length 0")
  Zone: // implicit z => // 0.5
    val fname = toCString(args.head)
    val argArray = makeStringArray(args) // take Scala strings, lower them to C level
    val envStrings = env.map((k, v) => s"$k=$v") // convert env pairs to execve format
    val envArray = makeStringArray(envStrings.toSeq) // lower them to C level

    val r = util.execve(fname, argArray, envArray) // execve never returns on success!
    if r != 0 then // execve failed
      val err = errno.errno // error code, like EINVAL
      stdio.printf(c"error: %d %d\n", err, string.strerror(err)) // eg "Invalid arguments"
      throw Exception(s"bad execve: returned $r")

  0 // This will never be reached, because execve does not return on success!

// Take Scala strings (args), turn them into a CString array, to be fed into execve.
def makeStringArray(args: Seq[String]): Ptr[CString] =
  val count = args.size
  val size = sizeof[Ptr[CString]] * count.toUSize + 1.toUSize // 1 extra for null
  val destArray = stdlib.malloc(size).asInstanceOf[Ptr[CString]]

  // convert each Scala string to C string, malloc for it, then copy it, null terminate it
  Zone:
    for (arg, index) <- args.zipWithIndex do
      val stringPtr = toCString(arg) // requires Zone
      val stringLen: USize = string.strlen(stringPtr)
      val destString = stdlib.malloc(stringLen).asInstanceOf[Ptr[Byte]]
      string.strncpy(destString, stringPtr, arg.size.toUSize) // 0.5
      destString(stringLen) = 0.toByte // null terminate // 0.5
      destArray(index) = destString // put string in array

  destArray(count) = null // last entry in array is null ptr, so execve knows it's the end
  destArray // we are returning a pointer given by malloc! that's why it's not freed.

def runOneAtATime(commands: Seq[Seq[String]]) =
  for command <- commands do doAndAwait(() => runCommand(command))

def runSimultaneously(commands: Seq[Seq[String]]) =
  val pids = for command <- commands yield doFork(() => runCommand(command))
  for pid <- pids do await(pid)

def awaitAny(pids: Set[Int]): Set[Int] =
  val status = stackalloc[Int](1)
  var running: Set[Int] = pids
  !status = 0
  val finished = util.waitpid(-1, status, 0)

  if running.contains(finished) then
    val statusCode = !status
    if statusCode != 0 then throw Exception(s"Child process returned error $statusCode")
    else pids - finished
  else throw Exception(s"error: reaped process ${finished}, expected one of $pids")

def awaitAll(pids: Set[Int]): Unit =
  var running = pids
  while running.nonEmpty do
    println(s"waiting for $running")
    running = awaitAny(running)

  println("Done!")

// TODO
// def badThrottle(commands: Seq[Seq[String]], maxParallel: Int) = ???

// TODO
// def goodThrottle(commands: Seq[Seq[String]], maxParallel: Int) = ???

@extern // Again, we can look up these in man pages. Some are syscalls, some not.
object util:
  def dup2(oldfd: Int, newfd: Int): Int = extern // copies fd content, gives it a new id

  // run program at filename, with command line args, and env (key=value pairs).
  // it replaces current running program's stack, heap, data. Does not return.
  def execve(filename: CString, args: Ptr[CString], env: Ptr[CString]): Int = extern
  def execvp(path: CString, args: Ptr[CString]): Int = extern // same as above, but no env

  def fork(): Int = extern // craete copy of process, make it a child. Returns TWICE!
  def getpid(): Int = extern // returns pid of calling process
  def pipe(pipes: Ptr[Int]): Int = extern // creates one-way data channel for IPC
  def strerror(errno: Int): CString = extern // gets err msg corresponding to errno,

  // wait for state changes in child, get info about child whose state changed.
  def waitpid(pid: Int, status: Ptr[Int], options: Int): Int = extern
