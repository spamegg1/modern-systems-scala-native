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

import scala.util.{Success,Failure}
import LibCurlConstants._
object main {
  def main(args:Array[String]):Unit = {
    if (args.length == 0) {
        println("usage: ./curl-out https://www.example.com")
        ???
    }

    println("initializing loop")
    implicit val loop = EventLoop
    val resp = Zone { implicit z =>
      for (arg <- args) {
        val url = arg
        val resp = Curl.startRequest(GET,url)

        resp.onComplete {
          case Success(data) =>
            println(s"got response for ${arg} - length ${data.body.size}")
            println(s"headers:")
            for (h <- data.headers) {
              println(s"request header: $h")
            }
            println(s"body: ${data.body}")
          case Failure(f) =>
            println("request failed",f)
        }
      }
    }

    loop.run()
    println("done")
  }
}
