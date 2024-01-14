package `01inputOutputTesting`

import scalanative.unsigned.UnsignedRichInt // convert Int to ULong to use _+_
import scalanative.unsafe.{CQuote, CString, CSize, Ptr, CChar, sizeof}
import scalanative.libc.{string, stdio, stdlib}

// Testing how malloc and strncpy work, how to handle null termination

@main // remember to comment / uncomment!
def testNullTermination: Unit =
  val cString: CString = c"hello" // uses CQuote
  val strLen: CSize = string.strlen(cString) // 5
  val buffer: Ptr[Byte] = stdlib.malloc(strLen + 1.toULong)

  // buffer(strLen.toULong) = 123.toByte // we can "update" a location like this

  // string.strncpy(buffer, cString, strLen) // copy, excluding \0
  string.strncpy(buffer, cString, strLen + 1.toULong) // copy, including \0

  // buffer(strLen.toULong) = 0.toByte // if we want to be super safe with \0

  for offset <- 0 until strLen.toInt + 1 // let's check null-termination!
  do
    val chr: CChar = buffer(offset)
    stdio.printf(
      c"the character '%c' is %d bytes long and has binary value %d\n",
      chr,
      sizeof[CChar],
      chr
    )

// the character 'h' is 1 bytes long and has binary value 104
// the character 'e' is 1 bytes long and has binary value 101
// the character 'l' is 1 bytes long and has binary value 108
// the character 'l' is 1 bytes long and has binary value 108
// the character 'o' is 1 bytes long and has binary value 111
// the character '' is 1 bytes long and has binary value 0

// If we uncomment buffer(strLen.toULong) = 123.toByte,
// and we copy strLen instead of strLen + 1.toULong, then it says:
// the character 'h' is 1 bytes long and has binary value 104
// the character 'e' is 1 bytes long and has binary value 101
// the character 'l' is 1 bytes long and has binary value 108
// the character 'l' is 1 bytes long and has binary value 108
// the character 'o' is 1 bytes long and has binary value 111
// the character '{' is 1 bytes long and has binary value 123

// I checked this program with valgrind, and it found 0 leaks / errors.
// The mystery is: how is my malloc-ed memory being freed?
