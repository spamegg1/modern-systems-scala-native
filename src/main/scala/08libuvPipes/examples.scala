/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
object FileInputPipeExample {
  import LibUV._, LibUVConstants._
  def main(args:Array[String]):Unit = {
    val p = FilePipe(c"./data.txt")
    .map { d =>
      println(s"consumed $d")
      d
    }.map { d =>
      val parsed = Try {
        d.toInt
      }
      println(s"parsed: $parsed")
      parsed
    }
    .addDestination(FileOutputPipe(c"./output.txt", false))
    uv_run(EventLoop.loop,UV_RUN_DEFAULT)
    println("done")
  }
}

object FileOutputPipeExample {
  import LibUV._, LibUVConstants._
  def main(args:Array[String]):Unit = {
    println("hello!")
    val p = SyncPipe(0)
    val p = FilePipe(c"./data.txt")

    val q = p.map { d =>
      println(s"consumed $d")
      d
    }.map { d =>
      val parsed = Try {
        d.toInt
      }
      println(s"parsed: $parsed")
      parsed.toString
    }
    .addDestination(FileOutputPipe(c"./output.txt", false))
    uv_run(EventLoop.loop,UV_RUN_DEFAULT)
    println("done")
  }
}

def filter(f:T => Boolean):Pipe[T] = {
  addDestination(mapOption { t =>
    f(t) match {
      case true => Some(t)
      case false => None
    }
  }
}

val p:Pipe[String,String] = ???
var counter = 0
p.map { i =>
  counter += 1
  i
}
// ...
uv_run(EventLoop.loop,UV_RUN_DEFAULT)
println(s"saw $counter elements")

val p:Pipe[String] = ???
val c = p.addDestination(Counter())
uv_run(EventLoop.loop,UV_RUN_DEFAULT)
println(s"saw ${c.counter} elements")

val p:Pipe[String] = ???
p.mapConcat { content =>
  content.split("\n")
}.mapConcat { line =>
  line.split(" ")
}.map { word =>
  println(s"saw word: ${word}")
}
uv_run(EventLoop.loop,UV_RUN_DEFAULT)
println(s"saw ${c.counter} elements")

SyncPipe(0)
.map { d =>
  println(s"consumed $d")
  d
}.map { d =>
  val parsed = Try {
    d.toInt
  }
}.filter {
  case Success(i) =>
    println(s"saw number $i")
    true
  case Failure(f) =>
    println(s"error: $f")
    false
}
// ...

val p:Pipe[String] = ???
p.mapAsync { url =>
  Curl.get(url)
}.map { response =>
  println(s"got back result: $response")

}
