/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
import scalanative.unsafe._
import scalanative.libc._
import scalanative.libc.stdio

import scala.scalanative.posix.unistd

object main {
  import util._

  def main(args:Array[String]):Unit = {
    println("about to exec")
    runCommand(Seq("/bin/ls", "-l", "."))
    println("exec returned, we're done!")
  }

  def runCommand(args:Seq[String], env:Map[String,String] = Map.empty):Int = {
    if (args.size == 0) { 
      throw new Exception("bad arguments of length 0")
    }
    Zone { implicit z =>
      val fname = toCString(args.head)
      val arg_array = makeStringArray(args)
      val env_strings = env.map { case (k,v) => s"$k=$v" }
      val env_array = makeStringArray(env_strings.toSeq)

      val r = execve(fname, arg_array, env_array)
      if (r != 0) {
        val err = errno.errno
        stdio.printf(c"error: %d %d\n", err, string.strerror(err) )
        throw new Exception(s"bad execve: returned $r")
      } 
    }
    ??? // This will never be reached.
  }

  def makeStringArray(args:Seq[String]):Ptr[CString] = {
    val pid = unistd.getpid()
    val dest_array = stdlib.malloc(sizeof[Ptr[CString]] * args.size + 1).asInstanceOf[Ptr[CString]]
    val count = args.size
    Zone { implicit z =>
      for ( (arg,i) <- args.zipWithIndex) {
        val string_ptr = toCString(arg)
        val string_len = string.strlen(string_ptr)
        val dest_str = stdlib.malloc(string_len + 1).asInstanceOf[Ptr[Byte]]
        string.strncpy(dest_str, string_ptr, arg.size + 1)
        dest_str(string_len) = 0
        dest_array(i) = dest_str
        ()
      }
      ()
    }
    dest_array(count) = null
    for (j <- (0 to count)) {
    }
    dest_array
  }

}

@extern
object util {
  def execve(filename:CString, args:Ptr[CString], env:Ptr[CString]):Int = extern
  def execvp(path:CString, args:Ptr[CString]):Int = extern
  def fork():Int = extern
  def getpid():Int = extern
  def waitpid(pid:Int, status:Ptr[Int], options:Int):Int = extern
  def strerror(errno:Int):CString = extern

  def pipe(pipes:Ptr[Int]):Int = extern
  def dup2(oldfd:Int, newfd:Int):Int = extern
}