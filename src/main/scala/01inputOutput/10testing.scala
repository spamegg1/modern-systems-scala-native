import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.*
import scalanative.libc.*
import stdlib.*
import string.*
import stdio.*

// @main
def testNullTermination: Unit =
  val cString: CString = c"hello"
  val strLen: CSize = strlen(cString)
  val buffer: Ptr[Byte] = malloc(strLen)

  strncpy(buffer, cString, strLen)

  for offset <- 0L to strLen.toLong
  do
    val chr: CChar = buffer(offset)
    stdio.printf(
      c"the character '%c' is %d bytes long and has binary value %d\n",
      chr,
      sizeof[CChar],
      chr
    )
