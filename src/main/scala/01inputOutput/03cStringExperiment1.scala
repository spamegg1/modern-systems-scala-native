package `01inputOutput`

import scalanative.unsafe.{CString, CChar, CQuote, sizeof}
import scalanative.libc.{StdioHelpers, string, stdio}

// @main
def cStringExperiment1: Unit =
  val str: CString = c"hello, world" // the c interpolator uses CQuote
  val strLen = string.strlen(str)

  // printf is an extension method, requires StdioHelpers
  stdio.printf(
    c"the string '%s' at address %p is %d bytes long\n",
    str,
    str,
    strLen
  )

  stdio.printf(
    c"the CString value str itself is %d bytes long\n",
    sizeof[CString]
  )

  for offset <- 0L to strLen.toLong
  do
    val chr: CChar = str(offset)
    stdio.printf(
      c"the character '%c' is %d bytes long and has binary value %d\n",
      chr,
      sizeof[CChar],
      chr
    )
