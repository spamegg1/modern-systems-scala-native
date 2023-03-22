package `02sortByCount`

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.*
import scalanative.libc.*
import stdlib.*
import string.*
import stdio.*

final case class WrappedArray[T](
    var data: Ptr[T],
    var used: Int,
    var capacity: Int
)

type NGramData = CStruct4[CString, Int, Int, Int] // 8 + 4 + 4 + 4 = 20 bytes

def makeWrappedArray(size: Int): WrappedArray[NGramData] =
  val data =
    malloc(size.toULong * sizeof[NGramData].toULong)
      .asInstanceOf[Ptr[NGramData]]
  WrappedArray[NGramData](data, 0, size)

def growWrappedArray(array: WrappedArray[NGramData], size: Int): Unit =
  val newCapacity = array.capacity + size
  val newSize = newCapacity.toULong * sizeof[NGramData]
  val newData = realloc(array.data.asInstanceOf[Ptr[Byte]], newSize)
  array.data = newData.asInstanceOf[Ptr[NGramData]]
  array.capacity = newCapacity

val byCount = CFuncPtr2.fromScalaFunction[Ptr[Byte], Ptr[Byte], Int](
  (p1: Ptr[Byte], p2: Ptr[Byte]) =>
    val nGramPtr1 = p1.asInstanceOf[Ptr[NGramData]]
    val nGramPtr2 = p2.asInstanceOf[Ptr[NGramData]]
    val count1 = nGramPtr1._2
    val count2 = nGramPtr2._2
    count2 - count1
)

val byCountNative = CFuncPtr2.fromScalaFunction[Ptr[Byte], Ptr[Byte], Int](
  (p1: Ptr[Byte], p2: Ptr[Byte]) =>
    val nGramPtr1 = p1.asInstanceOf[Ptr[NGramData]]
    val nGramPtr2 = p2.asInstanceOf[Ptr[NGramData]]
    val count1 = nGramPtr1._2
    val count2 = nGramPtr2._2
    if count1 > count2 then -1
    else if count1 == count2 then 0
    else 1
)

// run it with:
// ./target/scala-3.2.2/scala-native-out < ./src/main/resources/scala-native/googlebooks-eng-all-1gram-20120701-a
// reached EOF after 86618505 lines in 1358520 ms
// sorting done in 2764701 ms
// word 0: and 470825580
// word 1: and_CONJ 470334485
// word 2: and 381273613
// word 3: and_CONJ 380846175
// word 4: and 358027403
// word 5: and_CONJ 357625732
// word 6: and 341461347
// word 7: and_CONJ 341045795
// word 8: and 334803358
// word 9: and_CONJ 334407859
// word 10: and 313209351
// word 11: and_CONJ 312823075
// word 12: a 303316362
// word 13: a_DET 302961892
// word 14: and 285501930
// word 15: and_CONJ 285145298
// word 16: and 259728252
// word 17: and_CONJ 259398607
// word 18: and 255989520
// word 19: and_CONJ 255677047
// done.
// @main
def sortByCount(args: String*): Unit =
  val blockSize = 65536 * 16
  val lineBuffer = malloc(1024.toULong)
  var array = makeWrappedArray(blockSize)

  val readStart = System.currentTimeMillis()
  while stdio.fgets(lineBuffer, 1023, stdin) != null
  do
    if array.used == array.capacity then growWrappedArray(array, blockSize)
    parseLine(lineBuffer, array.data + array.used)
    array.used += 1

  val readElapsed = System.currentTimeMillis() - readStart
  stdio.fprintf(
    stdio.stderr,
    c"reached EOF after %d lines in %d ms\n",
    array.used,
    readElapsed
  )

  val sortStart = System.currentTimeMillis()
  qsort.qsort(
    array.data.asInstanceOf[Ptr[Byte]],
    array.used,
    sizeof[NGramData].toLong,
    byCount
  )
  val sortElapsed = System.currentTimeMillis() - sortStart
  stdio.printf(c"sorting done in %d ms\n", sortElapsed)

  val toShow = if array.used <= 20 then array.used else 20

  for i <- 0 until toShow
  do
    stdio.printf(
      c"word %d: %s %d\n",
      i,
      (array.data + i)._1,
      (array.data + i)._2
    )

  stdio.printf(c"done.\n")

val tempWord = malloc(1024.toULong)

def parseLine(lineBuffer: Ptr[Byte], data: Ptr[NGramData]): Unit =
  val count = data.at2
  val year = data.at3
  val docCount = data.at4

  val sscanfResult = stdio.sscanf(
    lineBuffer,
    c"%1023s %d %d %d\n",
    tempWord,
    year,
    count,
    docCount
  )
  if sscanfResult < 4 then throw new Exception("input error")

  val wordLength = strlen(tempWord).toULong
  val newString = malloc(wordLength + 1.toULong)
  strncpy(newString, tempWord, wordLength + 1.toULong)
  data._1 = newString

def freeArray(array: Ptr[NGramData], size: Int): Unit =
  for i <- 0 until size
  do
    val item = array + i
    stdlib.free(item._1)

  stdlib.free(array.asInstanceOf[Ptr[Byte]])

def parseLineNaive(fd: Ptr[stdio.FILE], array: Ptr[NGramData]): Unit =
  ??? // TODO

@extern
object qsort:
  def qsort(
      data: Ptr[Byte],
      num: Int,
      size: Long,
      comparator: CFuncPtr2[Ptr[Byte], Ptr[Byte], Int]
  ): Unit = extern
