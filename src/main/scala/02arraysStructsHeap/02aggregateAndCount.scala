package ch02.aggregateAndCount

import scalanative.unsigned.UnsignedRichInt // .toUSize
import scalanative.libc.{stdio, stdlib, string}
import scalanative.unsafe.{Ptr, stackalloc, sizeof, CString, CStruct4, CFuncPtr2, CQuote}
import scalanative.unsafe.extern

// These parts are the same as 01sortByCount.
final case class WrappedArray[T]( // same
    var data: Ptr[T],
    var used: Int,
    var capacity: Int
)

type NGramData = CStruct4[CString, Int, Int, Int] // same

def makeWrappedArray(size: Int): WrappedArray[NGramData] = // same
  val data = stdlib
    .malloc(size.toUSize * sizeof[NGramData]) // 0.5
    .asInstanceOf[Ptr[NGramData]]
  WrappedArray[NGramData](data, 0, size)

def growWrappedArray(array: WrappedArray[NGramData], size: Int): Unit = // same
  val newCapacity = array.capacity + size
  val newSize = newCapacity.toUSize * sizeof[NGramData] // 0.5
  val newData = stdlib.realloc(array.data.asInstanceOf[Ptr[Byte]], newSize)
  array.data = newData.asInstanceOf[Ptr[NGramData]]
  array.capacity = newCapacity

val byCount = // same
  CFuncPtr2.fromScalaFunction[Ptr[Byte], Ptr[Byte], Int]((p1: Ptr[Byte], p2: Ptr[Byte]) =>
    val ngramPtr1 = p1.asInstanceOf[Ptr[NGramData]]
    val ngramPtr2 = p2.asInstanceOf[Ptr[NGramData]]
    val count1 = ngramPtr1._2
    val count2 = ngramPtr2._2
    count2 - count1
  )

val lineBuffer = stdlib.malloc(1024.toUSize) // 0.5
val tempWordBuffer = stdlib.malloc(1024.toUSize) // 0.5
val blockSize = 1048576 // ~ 1MB - too big? (same)

// uncomment @main, make sure all other @main s in other files are commented
// use sbt> nativeLink to create executable
// run it with:
// ./target/scala-3.2.2/scala-native-out <
// ./src/main/resources/scala-native/googlebooks-eng-all-1gram-20120701-a
@main
def aggregateAndCount(args: String*): Unit =
  val array = makeWrappedArray(blockSize)
  val readStart = System.currentTimeMillis()
  val linesRead = readAllLines(stdio.stdin, array) // NEW, different
  val readElapsed = System.currentTimeMillis() - readStart

  println(s"""done. read $linesRead lines, ${array.used} unique words.
          $readElapsed ms""")

  val sortStart = System.currentTimeMillis() // same
  qsort.qsort(
    array.data.asInstanceOf[Ptr[Byte]],
    array.used,
    sizeof[NGramData].toLong,
    byCount
  )

  val sortElapsed = System.currentTimeMillis() - sortStart // same
  stdio.printf(c"sorting done in %d ms\n", sortElapsed)

  val toShow = if array.used <= 20 then array.used else 20 // same

  for i <- 0 until toShow // same
  do
    stdio.printf(
      c"word n: %s %d\n",
      (array.data + i)._1,
      (array.data + i)._2
    )

  println(c"done")

// Mostly the same, except reading and comparing one line at a time to minimize memory.
def readAllLines(fd: Ptr[stdio.FILE], array: WrappedArray[NGramData]): Long =
  var linesRead = 0L

  while stdio.fgets(lineBuffer, 1024, fd) != null
  do
    if array.used >= array.capacity - 1 then growWrappedArray(array, blockSize)
    parseAndCompare(lineBuffer, array) // this is the main difference!
    linesRead += 1

    if linesRead % 10000000 == 0 then
      stdio.printf(c"read %d lines, %d unique words so far\n", linesRead, array.used)
  linesRead

// New and different.
def parseAndCompare(line: CString, array: WrappedArray[NGramData]): Unit =
  val tempItem = stackalloc[NGramData](1) // only 1 NGramData at a time.
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
  string.strcmp(tempItem._1, prevItem._1)

def addNewItem(
    tempItem: Ptr[NGramData],
    nextItem: Ptr[NGramData]
): Unit =
  val tempWord: CString = tempItem._1
  val newWordLength = string.strlen(tempWord) // USize
  val newWordBuffer: Ptr[Byte] = stdlib.malloc(newWordLength + 1.toUSize) // 0.5

  string.strncpy(newWordBuffer, tempWord, newWordLength)
  newWordBuffer(newWordLength.toInt) = 0.toByte // null terminating the string // 0.5

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
