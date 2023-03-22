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

import argonaut._, Argonaut._

object Main {
  def main(args:Array[String]):Unit = {
    import argonaut._, Argonaut._
    val l:List[String] = List("list","of","strings")
    println(l.asJson.spaces2)
  // ...

    val m:Map[String,String] = Map(
      "key1" -> "value1",
      "key2" -> "value2"
    )

    printfJson(m)
  }

  def printfJson[T](data:T)(implicit e:EncodeJson[T]):Unit = 
    Zone { implicit z =>
      val stringData = data.asJson.spaces2
      val cstring = toCString(stringData)
      stdio.printf(c"rendered json: %s\n", cstring)
    }
}