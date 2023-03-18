import scala.scalanative.unsafe.*
import scala.scalanative.libc.*

def parseLine(line: CString, wordOut: CString, bufferSize: Int): Unit =
  val tempBuffer: Ptr[Byte] = stackalloc[Byte](1024)
  val maxWordLength = bufferSize - 1
  val scanResult = stdio.sscanf(line, c"%1023s\n", tempBuffer)

  if scanResult < 1 then throw new Exception(s"bad scanf result: $scanResult")

  val wordLength: CSize = string.strlen(tempBuffer)
  if wordLength.toInt >= maxWordLength then
    throw new Exception(
      s"word length $wordLength exceeds max buffer size $bufferSize"
    )

  //              dest      source     destSize
  string.strncpy(wordOut, tempBuffer, wordLength)

// @main
def goodSscanfStringParse: Unit =
  val lineInBuffer = stackalloc[Byte](1024)
  val wordOutBuffer = stackalloc[Byte](32)

  while stdio.fgets(lineInBuffer, 1023, stdio.stdin) != null
  do
    parseLine(lineInBuffer, wordOutBuffer, 32)
    stdio.printf(c"read word: '%s'\n", wordOutBuffer)
