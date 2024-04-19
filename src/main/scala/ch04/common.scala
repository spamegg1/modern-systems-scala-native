package ch04

import scalanative.unsigned.{USize, UnsignedRichInt} // .toUSize
import scalanative.unsafe.{CQuote, toCString, Ptr, CString}
import scalanative.unsafe.{Zone, stackalloc, sizeof, extern}
import scalanative.libc.{stdlib, string, stdio, errno}
import scalanative.posix.unistd // getpid

// This is a "conceptual" Scala version of execve
case class Command(path: String, args: String, env: Map[String, String])

def doFork(task: Function0[Int]): Int =
  val pid = util.fork() // fork returns twice: in calling process and in new process.
  if pid > 0 then // we are in the calling process
    println(s"forked, got pid ${pid}")
    pid // this is the PID of the newly created child process.
  else
    println(s"in proc ${unistd.getpid()}") // we are in the new child process
    val res = task.apply() // run task in new child process
    stdlib.exit(res) // make sure child terminates!
    res

def await(pid: Int): Int =
  val status = stackalloc[Int](1)
  util.waitpid(pid, status, 0) // wait for child to finish, get its status code
  val statusCode = !status
  if statusCode != 0 then throw Exception(s"Child process returned error $statusCode")
  statusCode

// fork a child to run task in, wait for it to finish, return its status
def doAndAwait(task: Function0[Int]): Int = await(doFork(task))

def runCommand(args: Seq[String], env: Map[String, String] = Map.empty): Int =
  if args.size == 0 then throw Exception("bad arguments of length 0")
  Zone: // implicit z => // 0.5
    println(s"proc ${unistd.getpid()}: running cmd ${args.head} with args ${args.tail}")
    val fileName = toCString(args.head)
    val argArray = makeStringArray(args) // take Scala strings, lower them to C level
    val envStrings = env.map((k, v) => s"$k=$v") // convert env pairs to execve format
    val envArray = makeStringArray(envStrings.toSeq) // lower them to C level

    val r = util.execve(fileName, argArray, envArray) // execve never returns on success!
    if r != 0 then // execve failed
      val err = errno.errno // error code, like EINVAL
      stdio.printf(c"error: %d %d\n", err, string.strerror(err)) // eg "Invalid arguments"
      throw Exception(s"bad execve: returned $r")

  0 // This will never be reached, because execve does not return on success!

// Take Scala strings (args), turn them into a CString array, to be fed into execve.
def makeStringArray(args: Seq[String]): Ptr[CString] =
  val count = args.size
  val size = sizeof[Ptr[CString]] * count.toUSize + 1.toUSize // 1 extra for null
  val destArray = stdlib.malloc(size).asInstanceOf[Ptr[CString]] // type pun

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

def runOneAtATime(commands: Seq[Seq[String]]) = // run multiple commands sequentially.
  for command <- commands do doAndAwait(() => runCommand(command))

// run all the commands at once, then wait for them (possibly out of sequence).
// But if we have work to do when a process exits, like launching
// a new process, or some other task, this approach doesn’t work.
def runSimultaneously(commands: Seq[Seq[String]]) =
  val pids = for command <- commands yield doFork(() => runCommand(command))
  for pid <- pids do await(pid)

// wait on one process among a set of processes, return pids of remaining unfinished ones.
def awaitAny(pids: Set[Int]): Set[Int] =
  val status = stackalloc[Int](1) // waitpid takes a pointer
  !status = 0
  val finished = util.waitpid(-1, status, 0) // wait for any child at all

  if pids.contains(finished) then // !status might have changed depending on child status
    val statusCode = !status // TODO: checkStatus(status)
    if statusCode != 0 then throw Exception(s"Child process returned error $statusCode")
    else pids - finished
  else throw Exception(s"error: reaped process ${finished}, expected one of $pids")

def awaitAll(pids: Set[Int]): Unit =
  var running = pids
  while running.nonEmpty do
    println(s"waiting for $running")
    running = awaitAny(running) // this shrinks running by one child process
  println("(awaitAll) Done!")

@extern // we can look up these in man pages. Some are syscalls, some not.
object util:
  def dup2(oldfd: Int, newfd: Int): Int = extern // copies fd content, gives it a new id
  // run program at filename, with command line args, and env (key=value pairs).
  // it replaces current running program's stack, heap, data. Does not return.
  def execve(filename: CString, args: Ptr[CString], env: Ptr[CString]): Int = extern
  def execvp(path: CString, args: Ptr[CString]): Int = extern // same as above, but no env
  def fork(): Int = extern // create copy of process, make it a child. Returns TWICE!
  def getpid(): Int = extern // returns pid of calling process
  def pipe(pipes: Ptr[Int]): Int = extern // creates one-way data channel for IPC
  def strerror(errno: Int): CString = extern // gets err msg corresponding to errno,
  // wait for state changes in child, get info about child whose state changed.
  def waitpid(pid: Int, status: Ptr[Int], options: Int): Int = extern
