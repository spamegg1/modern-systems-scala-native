package `01inputOutput`

import scalanative.unsafe.{CQuote, CString, CSize, CChar, Ptr, sizeof}
import scalanative.libc.{StdioHelpers, stdio, string}

// @main
def cStringExperiment2 =
  // CStrings are unsafe mutable byte buffers: CString = Ptr[CChar] = Ptr[Byte]
  val str: Ptr[Byte] = c"hello, world"
  val strLen: CSize = string.strlen(str) // 12 bytes long

  stdio.printf(
    c"the string '%s' at address %p is %d bytes long\n",
    str,
    str,
    strLen
  )

  // pointer is 8 bytes long and 8 < 12 but that's OK.
  stdio.printf(
    c"the Ptr[Byte] value 'str' itself is %d bytes long\n",
    sizeof[CString]
  )

  // dereferencing, or "looking up" is done with the ! operator.
  stdio.printf(c"dereferencing the pointer: %d\n", !str)

  for offset <- 0L to strLen.toLong
  do
    val charAddress: Ptr[Byte] = str + offset // Ptr[Byte] + Long = Ptr[Byte]
    val char: Byte = !charAddress
    stdio.printf(
      c"'%c'(%d) at address %p is %d bytes long\n",
      char,
      char,
      charAddress,
      sizeof[CChar]
    )
