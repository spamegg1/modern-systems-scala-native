package ch09
package jsonSimple

import scala.scalanative.unsafe.{toCString, CQuote, Zone}
import scala.scalanative.libc.stdio
import argonaut.{Argonaut, EncodeJson}, Argonaut.ToJsonIdentity

@main
def jsonSimple(args: String*): Unit =
  val l: List[String] = List("list", "of", "strings")
  println(l.asJson.spaces2)
  // ...

  val m: Map[String, String] = Map("key1" -> "value1", "key2" -> "value2")
  printfJson(m)

def printfJson[T](data: T)(using e: EncodeJson[T]): Unit =
  Zone:
    val stringData = data.asJson.spaces2
    val cstring = toCString(stringData)
    stdio.printf(c"rendered json: %s\n", cstring)
