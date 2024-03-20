package bug

import scalanative.unsafe.{CQuote, Ptr}
import scalanative.libc.stdio

// https://github.com/scala-native/scala-native/issues/3668
@main
def run: Unit =
  val str: Ptr[Byte] = c"hello"
  stdio.printf(c"dereferencing: %c", !str)
  // should print h but prints 0
