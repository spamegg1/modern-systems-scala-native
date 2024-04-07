package ch02
package agg

import scalanative.unsigned.UnsignedRichInt // .toUSize
import scalanative.unsafe.{Ptr, CSize, CQuote, CString, sizeof, stackalloc}
import scalanative.libc.{stdio, stdlib, string}, stdio.FILE

val lineBuffer = stdlib.malloc(1024) // 0.5
val tempWordBuffer = stdlib.malloc(1024)
val blockSize = 1048576 // ~ 1MB - too big? (same)

@main
def aggregateAndCount(args: String*): Unit =
  val array = makeWrappedArray(blockSize)
  val readStart = System.currentTimeMillis()
  val linesRead = readAllLines(stdio.stdin, array) // NEW, different
  val readElapsed = System.currentTimeMillis() - readStart

  println(s"""done. read $linesRead lines, ${array.used} unique words. $readElapsed ms""")

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

  for i <- 0 until toShow do // same
    stdio.printf(c"word n: %s %d\n", (array.data + i)._1, (array.data + i)._2)

  println(c"done")

// Mostly the same, except reading and comparing one line at a time to minimize memory.
def readAllLines(fd: Ptr[FILE], array: WrappedArray[NGramData]): Long =
  var linesRead = 0L

  while stdio.fgets(lineBuffer, 1024, fd) != null do
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
  val tempWord: CString = tempItem._1
  val tempCount: Ptr[Int] = tempItem.at2
  val tempYear: Ptr[Int] = tempItem.at3
  val tempDocCount: Ptr[Int] = tempItem.at4

  val sscanfResult = stdio.sscanf(
    line,
    c"%1023s %d %d %d\n",
    tempWord, // CString
    tempYear, // Ptr[Int]
    tempCount, // Ptr[Int]
    tempDocCount // Ptr[Int]
  )
  if sscanfResult < 4 then throw Exception("input error")
  true

def isItemNew(tempItem: Ptr[NGramData], prevItem: Ptr[NGramData]): Int =
  string.strcmp(tempItem._1, prevItem._1)

def addNewItem(tempItem: Ptr[NGramData], nextItem: Ptr[NGramData]): Unit =
  val tempWord: CString = tempItem._1
  val newWordLength = string.strlen(tempWord) // USize
  val newWordBuffer: Ptr[Byte] = stdlib.malloc(newWordLength + 1.toUSize) // 0.5

  string.strncpy(newWordBuffer, tempWord, newWordLength)
  newWordBuffer(newWordLength) = 0.toByte // null terminating the string

  nextItem._1 = newWordBuffer
  nextItem._2 = tempItem._2
  nextItem._3 = tempItem._3
  nextItem._4 = tempItem._4

def accumulateItem(tempItem: Ptr[NGramData], prevItem: Ptr[NGramData]): Unit =
  prevItem._2 = prevItem._2 + tempItem._2

// done. read 200 lines, 200 unique words. 1 ms
// sorting done in 0 ms
// word n: version 921
// word n: version 921
// word n: version 921
// word n: version 921
// word n: version 921
// word n: version 921
// word n: version 921
// word n: version 921
// word n: cable 274
// word n: cable 274
// word n: cable 274
// word n: cable 274
// word n: cable 274
// word n: cable 274
// word n: cable 274
// word n: cable 274
// word n: windows 444
// word n: windows 444
// word n: windows 444
// word n: windows 444
// Ptr@560694d6b091
