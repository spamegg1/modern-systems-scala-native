package ch01.sscanfInt

import scalanative.unsafe.{CString, Ptr, stackalloc, CQuote, sizeof, CInt}
import scalanative.libc.stdio

// def fgets(str: CString, count: CInt, stream: Ptr[FILE]): CString
// fgets will read one line of text of no more than count characters from file
// stream, and store it in the string str. str must already be allocated with at
// least count + 1 bytes of storage. fgets cannot check the bounds of the buffer
// for you. If fgets succeeds, it returns the pointer to buffer. If fgets fails,
// it returns null. Checking for failures is important: null is returned most
// commonly when fgets reaches the end of a file, or EOF.

@main
def sscanfIntExample: Unit =
  // allocate space on the stack for a line of up to 1KB input from user
  // inline def stackalloc[T](n: Int)(using Tag[T]): Ptr[T]
  val lineInBuffer: Ptr[Byte] = stackalloc[Byte](1024)

  // as long as the user is inputting data into standard input, parse it.
  while stdio.fgets(lineInBuffer, 1024 - 1, stdio.stdin) != null
  do parseIntLine(lineInBuffer) // returns an Int, but we discard it.

  println("done")

// here line: CString = Ptr[CChar] = Ptr[Byte]
def parseIntLine(line: CString): Int =
  // allocate space on the stack for the pointer to the integer that the user will input
  val intPointer: Ptr[Int] = stackalloc[Int](1)

  // line comes from fgets, it contains the line the user inputted. Scan it.
  // here line is a pointer to the beginning of a string.
  // def sscanf(s: CString, format: CString, vargs: Any*): CInt
  val scanResult: CInt = stdio.sscanf(line, c"%d\n", intPointer)

  // because of c"%d\n" the input must be exactly 1 integer per line.
  // If the format string was, say, c"%d %d\n" then we'd need 2 storage pointers
  // sscanf returns the count of items successfully matched. If 0, then error.
  if scanResult == 0 then throw Exception("parse error in sscanf")

  // Note that we are reusing the same address for intPointer, over and over.
  // So the next call allocates the SAME space. So the previous value will still
  // be there. It's not initialized to 0 or anything.
  // So the next call to parseIntLine will contain the previously read value.
  // The programmer must never read that data, and never return a stack pointer.
  // A stack pointer is only valid until the function in which it is called
  // returns. That means we should never return a stack pointer from a
  // function, and instead use them only for temporary storage. Neither C nor
  // Scala Native will prevent us from using an invalid pointer.
  val intValue: Int = !intPointer
  stdio.printf(c"read value %d into address %p\n", intValue, intPointer)

  // we could just avoid this, and return Unit instead.
  // But it's for demonstrating purposes: how to return safely from a stack ptr.
  intValue

// sbt:scala-native> run
// 1
// read value 1 into address 0x7ffceab81900
// 2
// read value 2 into address 0x7ffceab81900
// 3
// read value 3 into address 0x7ffceab81900
// 4
// read value 4 into address 0x7ffceab81900
// 5
// read value 5 into address 0x7ffceab81900
// asd
// java.lang.Exception: parse error in sscanf
