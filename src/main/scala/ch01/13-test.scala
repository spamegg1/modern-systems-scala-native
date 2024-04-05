package ch01.test

import scalanative.unsafe.{CQuote, CString, CSize, CChar, Ptr, sizeof}
import scalanative.libc.{stdio, string}

@main
def cStringExperiment2: Unit =
  val str: Ptr[Byte] = c"hello, world"
  val strLen: CSize = string.strlen(str)

  for offset <- 0 until strLen.toInt + 1
  do
    val charAddress: Ptr[Byte] = str + offset // Ptr[Byte] + Long = Ptr[Byte]
    val char: Byte = !charAddress
    stdio.printf(
      c"'%c'(%d) at address %p is %d byte long\n",
      char, // the character itself, using %c
      char, // the ASCII value of the character, using %d
      charAddress, // the address of the character, using %p
      sizeof[CChar] // the size of CChar, which is 1 byte
    )
