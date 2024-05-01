package ch01
package moreTesting

import scalanative.unsafe.{CQuote, sizeof, CStruct3, stackalloc, Ptr}
import scalanative.libc.{stdio, stdlib}

type Vec = CStruct3[Double, Double, Double]

// Works the same in C, with both gcc and clang. No problems, prints 34 after free.
// But valgrind finds 2 errors: illegal read and write (after free).
@main
def run =
  val vec: Ptr[Vec] = stackalloc[Vec](1)
  vec._1 = 10.0 // _1, _2 etc. give us the Double values in the fields directly,
  vec._2 = 20.0 // at1, at2 etc. give us pointers to the Double values instead.
  vec._3 = 30.0
  stdio.printf(c"%f\n", !vec.at2) // so we get the value by at2 then !

  val intPtr = stdlib.malloc(sizeof[Int])
  !intPtr = 2
  stdio.printf(c"The value of the int: %d\n", !intPtr) // prints 2, OK

  stdio.printf(c"freeing the memory allocated to the integer!\n")
  stdlib.free(intPtr)

  stdio.printf(c"attempting to update the integer that was just freed!\n")
  !intPtr = 34
  stdio.printf(c"The value of the int: %d\n", !intPtr) // prints 34
  // CANNOT GET SEGFAULT NO MATTER WHAT! I give up, moving on.
