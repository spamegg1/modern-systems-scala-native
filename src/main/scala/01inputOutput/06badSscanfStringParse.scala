import scalanative.unsafe.*
import scalanative.libc.*
import stdio.*

def parseStrLine(line: CString): Unit =
  var stringPointer: Ptr[CString] = stackalloc[CString](sizeof[CString])
  stdio.printf(
    c"allocated %d bytes for a string at %p\n",
    sizeof[CString],
    stringPointer
  )

  val scanResult = stdio.sscanf(line, c"%s\n", stringPointer)

  if scanResult < 1 then
    throw new Exception(s"insufficient matches in sscanf: $scanResult")

  stdio.printf(c"scan results: '%s'\n", stringPointer)

// @main
def badSscanfStringParse: Unit =
  val lineInBuffer = stackalloc[Byte](1024)
  val wordOutBuffer = stackalloc[Byte](32)

  while fgets(lineInBuffer, 1023, stdin) != null
  do parseStrLine(lineInBuffer)
