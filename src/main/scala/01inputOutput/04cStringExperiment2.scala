package `01inputOutputCstring2`

import scalanative.unsafe.{CQuote, CString, CSize, CChar, Ptr, sizeof}
import scalanative.libc.{stdio, string}

// @main
def cStringExperiment2 =
  // CStrings are unsafe mutable byte buffers: CString = Ptr[CChar] = Ptr[Byte]
  val str: Ptr[Byte] = c"hello, world" // CQuote is needed for c"..."
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

// address of string = adress of first char 'h' = 0x562419777df0
// (will be a different address for you)
// all addresses are contiguous, ending in f0, f1, f2, ..., fb, fc

// the string 'hello, world' at address 0x562419777df0 is 12 bytes long
// the Ptr[Byte] value 'str' itself is 8 bytes long
// dereferencing the pointer: 1674056544
// 'h'(104) at address 0x562419777df0 is 1 bytes long
// 'e'(101) at address 0x562419777df1 is 1 bytes long
// 'l'(108) at address 0x562419777df2 is 1 bytes long
// 'l'(108) at address 0x562419777df3 is 1 bytes long
// 'o'(111) at address 0x562419777df4 is 1 bytes long
// ','(44) at address 0x562419777df5 is 1 bytes long
// ' '(32) at address 0x562419777df6 is 1 bytes long
// 'w'(119) at address 0x562419777df7 is 1 bytes long
// 'o'(111) at address 0x562419777df8 is 1 bytes long
// 'r'(114) at address 0x562419777df9 is 1 bytes long
// 'l'(108) at address 0x562419777dfa is 1 bytes long
// 'd'(100) at address 0x562419777dfb is 1 bytes long
// ''(0) at address 0x562419777dfc is 1 bytes long
