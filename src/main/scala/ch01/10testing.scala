package ch01
package testNullTermination

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.{Zone, toCString, CQuote, CString, CSize, Ptr, CChar, sizeof}
import scalanative.libc.{string, stdio, stdlib}

// Testing how malloc, strncpy work, how to handle null termination

def generateHtml(text: String)(using Zone) =
  toCString(s"<h1>$text</h1>\n")

@main
def run: Unit = Zone:
  stdio.printf(generateHtml("hello"))
  // val cString: CString = c"hello" // uses CQuote
  // val strLen: CSize = string.strlen(cString) // 5
  // val buffer: Ptr[Byte] = stdlib.malloc(strLen + 1.toUSize) // 0.5

  // // buffer(strLen) = 123.toByte // we can "update" a location like this

  // // string.strncpy(buffer, cString, strLen) // copy, excluding \0
  // string.strncpy(buffer, cString, strLen + 1.toUSize) // 0.5: copy, including \0
  // buffer(strLen) = 0.toByte // if we want to be super safe with \0

  // for offset <- 0 until strLen.toInt + 1 do // let's check null-termination!
  //   val chr: CChar = buffer(offset) // pointer arithmetic = array lookup
  //   stdio.printf(c"'%c' is %d bytes long, has binary value %d\n", chr, sizeof[CChar], chr)

// 'h' is 1 bytes long, has binary value 104
// 'e' is 1 bytes long, has binary value 101
// 'l' is 1 bytes long, has binary value 108
// 'l' is 1 bytes long, has binary value 108
// 'o' is 1 bytes long, has binary value 111
// '' is 1 bytes long, has binary value 0

// If we uncomment buffer(strLen) = 123.toByte,
// and we copy strLen instead of strLen + 1.toUSize, then it says:
// 'h' is 1 bytes long, has binary value 104
// 'e' is 1 bytes long, has binary value 101
// 'l' is 1 bytes long, has binary value 108
// 'l' is 1 bytes long, has binary value 108
// 'o' is 1 bytes long, has binary value 111
// '{' is 1 bytes long, has binary value 123

// I checked this program with valgrind,, it found 0 leaks / errors.
// The mystery is: how is my malloc-ed memory being freed?
