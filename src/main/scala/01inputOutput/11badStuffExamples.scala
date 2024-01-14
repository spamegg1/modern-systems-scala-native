package `01inputOutputBadStuff`

import scalanative.unsigned.UnsignedRichInt // convert Int to ULong
import scalanative.unsafe.{CQuote, CString, CSize, Ptr, CChar, sizeof}
import scalanative.libc.{string, stdio, stdlib}

// @main // remember to comment / uncomment!
def testingBadStuff: Unit =
  // 1. Writing to read-only memory:
  // val string: Ptr[CChar] = c"hello"
  // stdio.printf(c"attempting to write to read-only memory!\n")
  // string(0.toULong) = 'a'.toByte // nativeLink OK, segfaults when ran.

  // 2. Dereferencing a null pointer, attempting to read its value
  // val ptr2: Ptr[Byte] = null
  // stdio.printf(c"attempting to dereference and read a null pointer!\n")
  // stdio.printf(c"%d", !ptr2) // nativeLink OK, but Java NPE when ran.

  // 3. Dereferencing a null pointer, attempting to update its value
  // val ptr3: Ptr[Byte] = null
  // stdio.printf(c"attempting to dereference and update a null pointer!\n")
  // !ptr3 = 1.toByte // nativeLink OK, but Java NPE when ran.

  // 4. Dereferencing a null pointer without doing anything
  // val ptr4: Ptr[Byte] = null
  // stdio.printf(c"attempting to dereference a null pointer!\n")
  // !ptr4 // nativeLink OK, but Java NPE when ran.

  // 5. Buffer overflow
  // array index 20 is past the end of the array (which contains 12 elements)
  // val str5: Ptr[CChar] = c"Hello world"
  // stdio.printf(c"attempting to access beyond the end of an array!\n")
  // val char: CChar = str5(20.toULong) // no errors!
  // stdio.printf(c"%c\n", char)

  // 6. Accessing an address that has been freed
  val intPtr: Ptr[Int] = stdlib.malloc(sizeof[Ptr[Int]]).asInstanceOf[Ptr[Int]]
  !intPtr = 2
  stdio.printf(c"The value of the int: %d\n", (!intPtr).toInt)

  stdio.printf(c"freeing the memory allocated to the integer pointer!\n")
  stdlib.free(intPtr.asInstanceOf[Ptr[Byte]])

  stdio.printf(c"attempting to update the integer that was just freed!\n")
  !intPtr = 34 // no errors!
  stdio.printf(c"The value of the int: %d\n", (!intPtr).toInt)

  // 7. Improper use of scanf
  // int n = 2;
  // scanf(" ", n); // no segfault! Damn.

  stdio.printf(c"all the bad stuff finished.\n")
