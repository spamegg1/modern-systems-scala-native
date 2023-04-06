package `01inputOutputCstring2`

import scalanative.unsafe.{CQuote, CString, CSize, CChar, Ptr, sizeof}
import scalanative.libc.{stdio, string}

// @main
def cStringExperiment2 =
  // CStrings are unsafe mutable byte buffers: CString = Ptr[CChar] = Ptr[Byte]
  val str: Ptr[Byte] = c"hello, world"
  val strLen: CSize = string.strlen(str) // 12 bytes long

  stdio.printf(
    c"the string '%s' at address %p is %d bytes long\n",
    str, // the string itself, using %s
    str, // the address of the beginning of the string, using %p
    strLen // this is 12
  )

  // pointer is 8 bytes long and 8 < 12 but that's OK.
  stdio.printf(
    c"the Ptr[Byte] value 'str' itself is %d bytes long\n",
    sizeof[CString] // this is 8, which is a 64-bit unsigned integer.
  )

  // dereferencing, or "looking up" is done with the ! operator.
  stdio.printf(c"dereferencing the pointer: %d\n", !str)

  for offset <- 0 until strLen.toInt + 1 // let's also check null termination
  do
    val charAddress: Ptr[Byte] = str + offset // Ptr[Byte] + Int = Ptr[Byte]
    val char: Byte = !charAddress
    stdio.printf(
      c"'%c'(%d) at address %p is %d bytes long\n",
      char, // the character itself, using %c
      char, // the ASCII value of the character, using %d
      charAddress, // the address of the character, using %p
      sizeof[CChar] // the size of CChar, which is 1 byte
    )
