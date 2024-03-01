package `04badExec`

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.*
import scalanative.libc.*
import scalanative.libc.stdio
import scalanative.posix.unistd

import util.*

@main
def badExec(args: String*): Unit =
  println("about to exec")
  runCommand(Seq("/bin/ls", "-l", "."))
  println("exec returned, we're done!")

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
  val destArray = stdlib
    .malloc(sizeof[Ptr[CString]] * args.size.toUSize) // 0.5
    .asInstanceOf[Ptr[CString]]
  val count = args.size

  Zone { // implicit z => // 0.5
    for (arg, i) <- args.zipWithIndex
    do
      val stringPtr = toCString(arg)
      val stringLen = string.strlen(stringPtr)
      val destStr = stdlib.malloc(stringLen) // .asInstanceOf[Ptr[Byte]] // 0.5
      string.strncpy(destStr, stringPtr, arg.size.toUSize) // 0.5
      // destStr(stringLen.toUInt) = 0 // 0.5
      destArray(i) = destStr
    ()
  }
  // destArray(count) = null
  // for j <- 0 to count do {}
  destArray

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
