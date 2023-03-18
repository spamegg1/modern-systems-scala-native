import scalanative.unsafe.{CString, Ptr, stackalloc, CQuote, sizeof}
import scalanative.libc.{stdio, StdioHelpers}

// @main
def sscanfIntExample: Unit =
  // allocate space on the stack for a line of input from user
  val lineInBuffer = stackalloc[Byte](1024)

  // as long as the user is inputting data into stdin, parse it.
  while stdio.fgets(lineInBuffer, 1023, stdio.stdin) != null
  do parseIntLine(lineInBuffer)

  println("done")

def parseIntLine(line: CString): Int =
  // allocate space on the stack for the integer that the user will input
  val intPointer: Ptr[Int] = stackalloc[Int](sizeof[Int])

  // line comes from fgets, it contains the line the user inputted. Scan it.
  val scanResult = stdio.sscanf(line, c"%d\n", intPointer)
  if scanResult == 0 then throw new Exception("parse error in sscanf")

  // Note that we are reusing the same address for intPointer, over and over.
  // So the next call allocates the SAME space. So the previous value will still
  // be there. It's not initialized to 0 or anything.
  // So the next call to parseIntLine will contain the previously read value.
  // The programmer must never read that data, and never return a stack pointer.
  val intValue = !intPointer
  stdio.printf(c"read value %d into address %p\n", intValue, intPointer)
  intValue
