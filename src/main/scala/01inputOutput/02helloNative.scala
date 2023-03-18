package `01inputOutput`

import scalanative.unsafe.*
import scalanative.libc.*

// @main
def helloNative: Unit =
  stdio.printf(c"hello native %s!\n", c"world")
