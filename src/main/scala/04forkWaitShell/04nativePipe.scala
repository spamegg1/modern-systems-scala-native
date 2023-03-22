/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
import scala.scalanative.unsafe._
import scala.scalanative.libc._

// import scalanative.native._
import scala.scalanative.posix.unistd
import scala.collection.mutable

case class Command(path:String, args:String, env:Map[String,String])
object main {
  import util._
  def main(args:Array[String]):Unit = {
    val status = pipeMany(0,1,Seq(
      Seq("/bin/ls", "."),
      Seq("/usr/bin/sort", "-r")
    ))
    println(s"- wait returned ${status}")
  }

  def doFork(task:Function0[Int]):Int = {
    val pid = fork()
    if (pid > 0) {
      pid
    } else {
      val res = task.apply()
      stdlib.exit(res)
      res
    }
  }

  def await(pid:Int):Int = {
    val status = stackalloc[Int]
    waitpid(pid, status, 0)
    val statusCode = !status
    if (statusCode != 0) {
      throw new Exception(s"Child process returned error $statusCode")
    }
    !status
  }

  def doAndAwait(task:Function0[Int]):Int = {
    val pid = doFork(task)
    await(pid)
  }

  def runCommand(args:Seq[String], env:Map[String,String] = Map.empty):Int = {
    if (args.size == 0) {
      throw new Exception("bad arguments of length 0")
    }
    Zone { implicit z =>
      println(s"- proc ${unistd.getpid()}: running command ${args.head} with args ${args}")
      val fname = toCString(args.head)
      val arg_array = stringSeqToStringArray(args)
      val env_strings = env.map { case (k,v) => s"$k=$v" }
      val env_array = stringSeqToStringArray(env_strings.toSeq)

      val r = execve(fname, arg_array, env_array)
      if (r != 0) {
        val err = errno.errno
        stdio.printf(c"error: %d %d\n", err, string.strerror(err) )
        throw new Exception(s"bad execve: returned $r")
      }
    }
    ??? // This will never be reached.
  }

  def stringSeqToStringArray(args:Seq[String]):Ptr[CString] = {
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
    dest_array
  }

// TODO: TEST
def runOneAtATime(commands:Seq[Seq[String]]) = {
  for (command <- commands) {
    doAndAwait { () =>
      runCommand(command)
    }
  }
}

//TODO: TEST
def runSimultaneously(commands:Seq[Seq[String]]) = {
  val pids = for (command <- commands) yield {
    doFork { () =>
      runCommand(command)
    }
  }
  for (pid <- pids) {
    await(pid)
  }
}

def awaitAny(pids:Set[Int]):Set[Int] = {
  val status = stackalloc[Int]
  var running = pids
  !status = 0
  val finished = waitpid(-1, status, 0)
  if (running.contains(finished)) {
    val statusCode = !status
    if (statusCode != 0) {
      throw new Exception(s"Child process returned error $statusCode")
    } else {
      return pids - finished
    }
  } else {
    throw new Exception(s"error: reaped process ${finished}, expected one of $pids")
  }
}

def awaitAll(pids:Set[Int]):Unit = {
  var running = pids
  while (running.nonEmpty) {
    println(s"- waiting for $running")
    running = awaitAny(running)
  }
  println("- Done!")
}


// TODO
def badThrottle(commands:Seq[Seq[String]], maxParallel:Int) = {
  ???
}

// TODO
def goodThrottle(commands:Seq[Seq[String]], maxParallel:Int) = {
  ???
}

def pipeMany(input:Int, output:Int, procs:Seq[Seq[String]]):Int = {
  val pipe_array = stackalloc[Int](2 * (procs.size - 1))
  var input_fds = mutable.ArrayBuffer[Int](input)
  var output_fds = mutable.ArrayBuffer[Int]()
  // create our array of pipes
  for (i <- 0 until (procs.size - 1)) {
    val array_offset = i * 2
    val pipe_ret = util.pipe(pipe_array + array_offset)
    output_fds += pipe_array(array_offset + 1)
    input_fds += pipe_array(array_offset)
  }
  output_fds += output

  val procsWithFds = (procs, input_fds, output_fds).zipped
  val pids = for ((proc, input_fd, output_fd) <- procsWithFds) yield {
    doFork { () =>
      // close all pipes that this process won't be using.
      for (p <- 0 until (2 * (procs.size - 1))) {
        if (pipe_array(p) != input_fd && pipe_array(p) != output_fd) {
          unistd.close(pipe_array(p))
        }
      }
      // reassign STDIN if we aren't at the front of the pipeline
      if (input_fd != input) {
        unistd.close(unistd.STDIN_FILENO)
        util.dup2(input_fd, unistd.STDIN_FILENO)
      }
      // reassign STDOUT if we aren't at the end of the pipeline
      if (output_fd != output) {
        unistd.close(unistd.STDOUT_FILENO)
        util.dup2(output_fd, unistd.STDOUT_FILENO)
      }
      runCommand(proc)
    }
  }

  for (i <- 0 until (2 * (procs.size - 1))) {
    unistd.close(pipe_array(i))
  }
  unistd.close(input)

  var waiting_for = pids.toSet
  while (!waiting_for.isEmpty) {
    val wait_result = waitpid(-1,null,0)
    println(s"- waitpid returned ${wait_result}")
    waiting_for = waiting_for - wait_result
  }
  return 0
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
