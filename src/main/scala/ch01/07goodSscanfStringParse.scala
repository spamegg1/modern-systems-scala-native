package ch01.goodSscanf

import scalanative.unsafe.{CString, Ptr, stackalloc, CQuote, CSize}
import scalanative.libc.{stdio, string}

def parseLine(line: CString, wordOut: CString, bufferSize: Int): Unit =
  // note that 1024 is not related to bufferSize. It's just a large enough safe number.
  val tempBuffer: Ptr[Byte] = stackalloc[Byte](1024) // no need to free.
  val maxWordLength = bufferSize - 1

  val scanResult = stdio.sscanf(line, c"%1023s\n", tempBuffer)
  if scanResult < 1 then throw Exception(s"bad scanf result: $scanResult")

  val wordLength: CSize = string.strlen(tempBuffer)
  if wordLength.toInt >= maxWordLength then // disallow segfault
    throw Exception(s"word length $wordLength exceeds max buffer size $bufferSize")

  //              dest      source     destSize
  string.strncpy(wordOut, tempBuffer, wordLength) // can't return tempBuffer, so copy it.
  // wordOut is also stack allocated, but it comes from a function that calls this one.

// first,  stdin        -> lineInBuffer,  using fgets
// second, lineInBuffer -> tempBuffer,    using sscanf
// third,  tempBuffer   -> wordOutBuffer, using strncpy
@main
def goodSscanfStringParse: Unit =
  val lineInBuffer = stackalloc[Byte](1024) // this size matches tempBuffer
  val wordOutBuffer = stackalloc[Byte](32) // to store result of parseLine

  while stdio.fgets(lineInBuffer, 1023, stdio.stdin) != null do
    parseLine(lineInBuffer, wordOutBuffer, 32) // now, user inputs >= 32 chars will fail!
    stdio.printf(c"read word: '%s'\n", wordOutBuffer)
