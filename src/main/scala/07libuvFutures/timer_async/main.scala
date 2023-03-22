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
import scala.concurrent.duration._
object Main {
  def main(args:Array[String]):Unit = {
    println("hello")
    implicit val loop = EventLoop
    println("setting up timer")
    Timer.delay(2.seconds).map { _ =>
      println("timer done!")
    }
    println("about to invoke loop.run()")
    loop.run()
    println("done!")
  }
}