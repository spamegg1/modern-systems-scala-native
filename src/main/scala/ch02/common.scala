package ch02

import scalanative.unsigned.UnsignedRichInt // .toUSize
import scalanative.unsafe.{Ptr, sizeof, CString, CStruct4, CFuncPtr2, CQuote, extern}
import scalanative.libc.{stdio, stdlib, string}
import scala.scalanative.unsigned.{ULong, USize}
import scalanative.unsafe.CSize

// First, data definitions.
type NGramData = CStruct4[CString, Int, Int, Int] // 8 + 4 + 4 + 4 = 20 bytes

// We need an array of NGramData's.
final case class WrappedArray[T]( // here T = NGramData
    var data: Ptr[T], // pointer to the start of an array of multiple NGramData structs.
    var used: Int, // how many structs are currently used
    var capacity: Int // how many structs it can hold
)

// allocate an array of uninitialized NGramData structs. size = how many NGrams we want.
def makeWrappedArray(size: Int): WrappedArray[NGramData] =
  val data: Ptr[NGramData] = stdlib
    .malloc(size.toUSize * sizeof[NGramData]) // 0.5: use .toUSize
    .asInstanceOf[Ptr[NGramData]]
  WrappedArray[NGramData](data, 0, size) // when we free this, we will free data only.

// Grow an array's capacity by given size. realloc copies existing data if necessary.
def growWrappedArray(array: WrappedArray[NGramData], size: Int): Unit =
  val newCapacity: Int = array.capacity + size
  val newSize: USize = newCapacity.toUSize * sizeof[NGramData] // 0.5: use .toUSize
  val newData: Ptr[Byte] = stdlib.realloc(array.data.asInstanceOf[Ptr[Byte]], newSize)
  array.data = newData.asInstanceOf[Ptr[NGramData]] // update in-place
  array.capacity = newCapacity // update in-place

// Here, array = data in a WrappedArray. Long sequence of NGramData's.
def freeArray(array: Ptr[NGramData], size: Int): Unit =
  // First, free all the strings that structs are pointing at.
  for i <- 0 until size do
    val item: Ptr[NGramData] = array + i // 0.5
    stdlib.free(item._1) // string is the first field of the struct.
  stdlib.free(array.asInstanceOf[Ptr[Byte]]) // now free the structs themselves.

// Two comparison functions. This is a bit inefficient.
val byCountNaive = CFuncPtr2.fromScalaFunction[Ptr[Byte], Ptr[Byte], Int]:
  (p1: Ptr[Byte], p2: Ptr[Byte]) =>
    val nGramPtr1 = p1.asInstanceOf[Ptr[NGramData]]
    val nGramPtr2 = p2.asInstanceOf[Ptr[NGramData]]
    val count1 = nGramPtr1._3 // count is the third field, book is wrong, it has ._2
    val count2 = nGramPtr2._3
    if count1 > count2 then -1 // these are a bit inefficient
    else if count1 == count2 then 0
    else 1

// This one is a bit more efficient, returns neg, 0, pos.
val byCount = CFuncPtr2.fromScalaFunction[Ptr[Byte], Ptr[Byte], Int]:
  (p1: Ptr[Byte], p2: Ptr[Byte]) =>
    val nGramPtr1 = p1.asInstanceOf[Ptr[NGramData]]
    val nGramPtr2 = p2.asInstanceOf[Ptr[NGramData]]
    val count1 = nGramPtr1._3
    val count2 = nGramPtr2._3
    count2 - count1

@extern
object qsort:
  def qsort(
      data: Ptr[Byte],
      num: Int,
      size: Long,
      comparator: CFuncPtr2[Ptr[Byte], Ptr[Byte], Int]
  ): Unit = extern
