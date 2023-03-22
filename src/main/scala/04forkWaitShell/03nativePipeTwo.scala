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

case class Command(path:String, args:String, env:Map[String,String])
object main {
  import util._
  def main(args:Array[String]):Unit = {
    println("about to fork")
    val status = runTwoAndPipe(0,1,Seq("/bin/ls", "."), Seq("/usr/bin/sort", "-r"))
    println(s"wait status ${status}")
  }

  def doFork(task:Function0[Int]):Int = {
    val pid = fork()
    if (pid > 0) {
      println(s"forked, got pid ${pid}")
      pid
    } else {
      println(s"in proc ${unistd.getpid()}")
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
      println(s"proc ${unistd.getpid()}: running command ${args.head} with args ${args}")
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
    for (j <- (0 to count)) {
    }
    dest_array
  }

def runOneAtATime(commands:Seq[Seq[String]]) = {
  for (command <- commands) {
    doAndAwait { () =>
      runCommand(command)
    }
  }
}

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
    val statusCode = !status // TODO: check_status(status)
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
    println(s"waiting for $running")
    running = awaitAny(running)
  }
  println("Done!")
}


def badThrottle(commands:Seq[Seq[String]], maxParallel:Int) = {
  ???
}

def goodThrottle(commands:Seq[Seq[String]], maxParallel:Int) = {
  ???
}

def runTwoAndPipe(input:Int, output:Int, proc1:Seq[String],
                  proc2:Seq[String]):Int = {
  val pipe_array = stackalloc[Int](2)
  val pipe_ret = util.pipe(pipe_array)
  println(s"pipe() returned ${pipe_ret}")
  val output_pipe = pipe_array(1)
  val input_pipe = pipe_array(0)

  val proc1_pid = doFork { () =>
    if (input != 0) {
      println(s"proc ${unistd.getpid()}: about to dup ${input} to stdin" )
      util.dup2(input, 0)
    }
    println(s"proc 1 about to dup ${output_pipe} to stdout")
    util.dup2(output_pipe, 1)
    stdio.printf(c"process %d about to runCommand\n", unistd.getpid())
    runCommand(proc1)
  }


  val proc2_pid = doFork { () =>
    println(s"proc ${unistd.getpid()}: about to dup")
    util.dup2(input_pipe, 0)
    if (output != 1) {
      util.dup2(output, 1)
    }
    unistd.close(output_pipe)
    stdio.printf(c"process %d about to runCommand\n", unistd.getpid())
    runCommand(proc2)
  }

  unistd.close(input)
  unistd.close(output_pipe)
  unistd.close(input_pipe)
  val waiting_for = Seq(proc1_pid, proc2_pid)
  println(s"waiting for procs: ${waiting_for}")
  val r1 = waitpid(-1, null, 0)
  println(s"proc $r1 returned")
  val r2 = waitpid(-1, null, 0)
  println(s"proc $r2 returned")
  r2
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
