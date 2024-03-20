package ch01.moreTesting

import scalanative.unsafe.{CQuote, sizeof}
import scalanative.libc.{stdio, stdlib}

// Works the same in C, with both gcc and clang. No problems, prints 34 after free.
// But valgrind finds 2 errors: illegal read and write (after free).
@main
def run =
  val intPtr = stdlib.malloc(sizeof[Int])
  !intPtr = 2
  stdio.printf(c"The value of the int: %d\n", !intPtr) // prints 2, OK

  stdio.printf(c"freeing the memory allocated to the integer!\n")
  stdlib.free(intPtr)

  stdio.printf(c"attempting to update the integer that was just freed!\n")
  !intPtr = 34
  stdio.printf(c"The value of the int: %d\n", !intPtr) // prints 34
  // CANNOT GET SEGFAULT NO MATTER WHAT!
  // I give up, moving on.
