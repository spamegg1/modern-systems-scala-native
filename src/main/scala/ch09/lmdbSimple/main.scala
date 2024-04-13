package ch09
package lmdbSimple

import scalanative.unsigned.{UnsignedRichInt, UnsignedRichLong} // .toUSize
import scalanative.unsafe.*
import scalanative.libc.{stdlib, stdio, string}

val lineBuffer = stdlib.malloc(1024) // 0.5
val getKeyBuffer = stdlib.malloc(512) // 0.5
val putKeyBuffer = stdlib.malloc(512) // 0.5
val valueBuffer = stdlib.malloc(512) // 0.5

@main
def lmdbSimple: Unit =
  val env = LMDB.open(c"./db")
  stdio.printf(c"opened db %p\n", env)
  stdio.printf(c"> ")

  while stdio.fgets(lineBuffer, 1024, stdio.stdin) != null do
    val putScanResult = stdio.sscanf(lineBuffer, c"put %s %s", putKeyBuffer, valueBuffer)
    val getScanResult = stdio.sscanf(lineBuffer, c"get %s", getKeyBuffer)

    if putScanResult == 2 then
      stdio.printf(c"storing value %s into key %s\n", putKeyBuffer, valueBuffer)
      LMDB.put(env, putKeyBuffer, valueBuffer)
      stdio.printf(c"saved key: %s value: %s\n", putKeyBuffer, valueBuffer)
    else if getScanResult == 1 then
      stdio.printf(c"looking up key %s\n", getKeyBuffer)
      val lookup = LMDB.get(env, getKeyBuffer)
      stdio.printf(c"retrieved key: %s value: %s\n", getKeyBuffer, lookup)
    else println("didn't understand input")

    stdio.printf(c"> ")

  println("done")
