package ch01.cStringExpr1

import scalanative.unsafe.{CString, CChar, CQuote, sizeof, CSize}
import scalanative.libc.{string, stdio}

@main
def cStringExperiment1: Unit =
  // a CString always has trailing 0 (null termination) by default.
  // type CString = Ptr[CChar] = Ptr[Byte]
  val str: CString = c"hello, world" // the c interpolator uses CQuote

  // def strlen(str: CString): CSize
  val strLen: CSize = string.strlen(str) // this is 12

  stdio.printf(
    c"the string '%s' at address %p is %d bytes long\n",
    str, // the string, using %s
    str, // address of the string, using %p
    strLen // this is CSize 12
  )

  // def sizeof[T](implicit tag: Tag[T]): CSize
  stdio.printf(
    c"the CString value str itself is %d bytes long\n",
    sizeof[CString] // this is CSize 8, 64-bit unsigned integer, using %d
  )

  for offset <- 0 until strLen.toInt + 1 do // this is 12 + 1, including \0
    val chr: CChar = str(offset) // look up character, use string like an array
    stdio.printf(
      c"the character '%c' is %d bytes long and has binary value %d\n",
      chr, // the character, using %c
      sizeof[CChar], // each character is CSize 1 byte, using %d
      chr // the actual value of the character, using %d
    )

// the string 'hello, world' at address 0x55ac830a6960 is 12 bytes long
// the CString value str itself is 8 bytes long
// the character 'h' is 1 bytes long and has binary value 104    0
// the character 'e' is 1 bytes long and has binary value 101    1
// the character 'l' is 1 bytes long and has binary value 108    2
// the character 'l' is 1 bytes long and has binary value 108    3
// the character 'o' is 1 bytes long and has binary value 111    4
// the character ',' is 1 bytes long and has binary value 44     5
// the character ' ' is 1 bytes long and has binary value 32     6
// the character 'w' is 1 bytes long and has binary value 119    7
// the character 'o' is 1 bytes long and has binary value 111    8
// the character 'r' is 1 bytes long and has binary value 114    9
// the character 'l' is 1 bytes long and has binary value 108    10
// the character 'd' is 1 bytes long and has binary value 100    11
// the character ' ' is 1 bytes long and has binary value 0      12
// with the null terminator, there are 13 characters.
