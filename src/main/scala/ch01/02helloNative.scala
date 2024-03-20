package ch01.helloNative

import scalanative.unsafe.CQuote
import scalanative.libc.stdio.printf

@main // don't forget to comment/uncomment!
def helloNative: Unit =
  printf(c"hello native %s!\n", c"world")

// here CQuote is needed for the c string interpolator, and
// libc.StdioHelpers.printf has signature:
//   def printf(format: CString, args: CVarArg*): CInt
// libc.stdio.printf has signature:
//   def printf(format: CString, vargs: Any*): CInt

// CString is an unsafe, mutable byte buffer with few methods.
