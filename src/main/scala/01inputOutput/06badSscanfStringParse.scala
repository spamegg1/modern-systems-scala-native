package ch01.badSscanf

import scalanative.unsafe.{CString, CQuote, Ptr, stackalloc, sizeof}
import scalanative.libc.stdio
// import scalanative.unsigned.UnsignedRichInt

// here line: CString = Ptr[CChar] = Ptr[Byte]
def parseStrLine(line: CString): Unit =
  // in the book stackalloc never has a number argument.
  // It has a default argument of 1.toULong, but in Scala 3 we need empty parentheses.
  // So the mistake is thinking: "I allocated enough space for a CString!"
  // But we don't know the size of the string.
  val stringPointer: Ptr[CString] = stackalloc[CString]() // this is 1.toULong
  stdio.printf(
    c"allocated %d bytes for a string at %p\n",
    sizeof[CString], // this is CChar = Ptr[Byte] = 8 bytes
    stringPointer
  )
  // line will be coming from stdin. Returns number of bytes scanned.
  val scanResult = stdio.sscanf(line, c"%s\n", stringPointer)

  if scanResult < 1 then throw Exception(s"insufficient matches in sscanf: ${scanResult}")

  stdio.printf(c"scan results: '%s'\n", stringPointer)
  // stackalloc does not need to be freed!

@main // don't forget to comment / uncomment!
def badSscanfStringParse: Unit =
  val lineInBuffer: Ptr[Byte] = stackalloc[Byte](1024) // this is to store stdin
  while stdio.fgets(lineInBuffer, 1024 - 1, stdio.stdin) != null
  do parseStrLine(lineInBuffer)

// I can get segfault if the input string is about > 100 chars long.
// I also do not understand why wordOutBuffer is there. It does nothing!
// I tried using the author's version of the code (Scala 2.11, Native 0.4.0)
// and it gives the segfault as described in the book, with 8 characters.
// I can only guess that it has something to do with how Scala Native changed,
// and how stackalloc is being used. But how can I reproduce the segfault here?
// Well, the book at least says that a segfault is not guaranteed to happen.
// I'm guessing that now, Scala Native produces binaries that have a long enough
// memory range (even with a hello world program) that it takes quite a while
// to go outside that range to get a segfault.
// I'll have to drop down to C to get a "reliable" and "reproducible" segfault.
// Oh well... that didn't work either. The C version of this program can only
// "malloc(): corrupted top size" at around 25-30 characters, and not reliably.
// I give up!
