/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
import scalanative.native._
import scala.util.{Success,Failure}

object main {
  def main(args:Array[String]):Unit = {
    if (args.length == 0) {
        println("usage: ./curl-out https://whatever.url/you/want?to=fetch")
        ???
    }

    println("initializing loop")
    implicit val loop = EventLoop
    val resp = Zone { implicit z => 
      for (arg <- args) {
        val url = toCString(arg)
        val resp = Curl.get(url)

        resp.onComplete { 
          case Success(body) =>
            println(s"got back response for ${arg} - body of length ${body.size}")
            println(body.substring(0,80))
            println("...")
            println(body.substring(body.size - 80))
          case Failure(f) =>
            println("request failed",f)
        }
      }
    }

    loop.run()
    println("loop done, cleaning up")
    Curl.cleanup()
    println("done")
  }

