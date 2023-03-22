package `02aggregate`

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

type NGramData = CStruct4[CString, Int, Int, Int]

def makeWrappedArray(size: Int): WrappedArray[NGramData] =
  val data = malloc(size.toULong * sizeof[NGramData].toULong)
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
    val ngramPtr1 = p1.asInstanceOf[Ptr[NGramData]]
    val ngramPtr2 = p2.asInstanceOf[Ptr[NGramData]]
    val count1 = ngramPtr1._2
    val count2 = ngramPtr2._2
    count2 - count1
)

val lineBuffer = malloc(1024.toULong)
val tempWordBuffer = malloc(1024.toULong)
val blockSize = 65536 * 16 // ~ 1MB - too big?

// uncomment @main, make sure all other @main s in other files are commented
// use sbt> nativeLink to create executable
// run it with:
// ./target/scala-3.2.2/scala-native-out < ./src/main/resources/scala-native/googlebooks-eng-all-1gram-20120701-a
// @main
def aggregateAndCount(args: String*): Unit =
  val array = makeWrappedArray(blockSize)
  val readStart = System.currentTimeMillis()
  val linesRead = readAllLines(stdin, array)
  val readElapsed = System.currentTimeMillis() - readStart

  println(s"""done. read $linesRead lines, ${array.used} unique words.
          $readElapsed ms""")

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
  do stdio.printf(c"word n: %s %d\n", (array.data + i)._1, (array.data + i)._2)

  println(c"done")

def readAllLines(
    fd: Ptr[stdio.FILE],
    array: WrappedArray[NGramData]
): Long =
  var linesRead = 0L

  while stdio.fgets(lineBuffer, 1024, fd) != null
  do
    if array.used >= array.capacity - 1 then growWrappedArray(array, blockSize)
    parseAndCompare(lineBuffer, array)
    linesRead += 1

    if linesRead % 10000000 == 0 then
      stdio.printf(
        c"read %d lines, %d unique words so far\n",
        linesRead,
        array.used
      )
  linesRead

def parseAndCompare(line: CString, array: WrappedArray[NGramData]): Unit =
  val tempItem = stackalloc[NGramData](sizeof[NGramData])
  tempItem._1 = tempWordBuffer
  val nextItem = array.data + array.used
  scanItem(line, tempItem)

  if array.used == 0 then
    addNewItem(tempItem, nextItem)
    array.used += 1
  else
    val prevItem = array.data + (array.used - 1)
    if isItemNew(tempItem, prevItem) != 0 then
      addNewItem(tempItem, nextItem)
      array.used += 1
    else accumulateItem(tempItem, prevItem)

def scanItem(line: CString, tempItem: Ptr[NGramData]): Boolean =
  val tempWord = tempItem._1
  val tempCount = tempItem.at2
  val tempYear = tempItem.at3
  val tempDocCount = tempItem.at4

  val sscanfResult = stdio.sscanf(
    line,
    c"%1023s %d %d %d\n",
    tempWord,
    tempYear,
    tempCount,
    tempDocCount
  )
  if sscanfResult < 4 then throw new Exception("input error")
  true

def isItemNew(tempItem: Ptr[NGramData], prevItem: Ptr[NGramData]): Int =
  strcmp(tempItem._1, prevItem._1)

def addNewItem(
    tempItem: Ptr[NGramData],
    nextItem: Ptr[NGramData]
): Unit =
  val tempWord: CString = tempItem._1
  val newWordLength: CSize = strlen(tempWord) // ULong
  val newWordBuffer: Ptr[Byte] = malloc(newWordLength) // + 1.toULong)

  strncpy(newWordBuffer, tempWord, newWordLength)
  // newWordBuffer(newWordLength.toULong) = 0 // null terminating the string

  nextItem._1 = newWordBuffer
  nextItem._2 = tempItem._2
  nextItem._3 = tempItem._3
  nextItem._4 = tempItem._4

def accumulateItem(
    tempItem: Ptr[NGramData],
    prevItem: Ptr[NGramData]
): Unit =
  prevItem._2 = prevItem._2 + tempItem._2

@extern
object qsort:
  def qsort(
      data: Ptr[Byte],
      num: Int,
      size: Long,
      comparator: CFuncPtr2[Ptr[Byte], Ptr[Byte], Int]
  ): Unit = extern
