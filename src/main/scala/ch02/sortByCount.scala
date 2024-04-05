package ch02.sortByCount

import scalanative.unsigned.UnsignedRichInt // .toUSize
import scalanative.unsafe.{Ptr, CQuote, CSize, sizeof}
import scalanative.libc.{stdio, stdlib, string}
import ch02.common
import common.{qsort, NGramData, byCount}

// this temporary space is used to read the string in each line of the file.
val tempWord: Ptr[Byte] = stdlib.malloc(1024) // 0.5

// We convert a line of the form: ngram TAB year TAB match_count TAB volume_count NEWLINE
// to an NGramData struct: [CString, Int, Int, Int]
// We read the string pointed to by lineBuffer, into the struct pointed to by data.
// Both lineBuffer and data are set-up by other functions / code.
def parseLine(lineBuffer: Ptr[Byte], data: Ptr[NGramData]): Unit =
  val year: Ptr[Int] = data.at2 // these are yet uninitialized!
  val count: Ptr[Int] = data.at3
  val docCount: Ptr[Int] = data.at4

  val sscanfResult = stdio.sscanf(
    lineBuffer, // src: read from
    c"%1023s %d %d %d\n", // word year count docCount = 8 4 4 4
    tempWord, // dest: word. Don't put into data directly yet! We don't know its length.
    year, // dest: year (int, 4 bytes)
    count, // dest: count (int, 4 bytes)
    docCount // dest: docCount (int, 4 bytes)
  )
  if sscanfResult < 4 then throw Exception("input error")

  // word length is not known ahead of time, so safely copy it from tempWord -> to data
  val wordLength: CSize = string.strlen(tempWord) // 0.5
  val newString: Ptr[Byte] = stdlib.malloc(wordLength + 1.toUSize) // 0.5
  string.strncpy(newString, tempWord, wordLength + 1.toUSize) // 0.5
  data._1 = newString // update in-place, initialization of struct complete.

// MAIN
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
@main
def sortByCount(args: String*): Unit =
  val blockSize = 1048576 // 2^20 NGramData items = 2^20 * 20 bytes = 20 MB
  val lineBuffer = stdlib.malloc(1024) // 0.5
  var array = common.makeWrappedArray(blockSize)
  val readStart = System.currentTimeMillis()

  // pipe file into stdin, then read line by line (fgets respects newlines)
  while stdio.fgets(lineBuffer, 1024, stdio.stdin) != null do // reads <= 1024 - 1 chars
    if array.used >= array.capacity then common.growWrappedArray(array, blockSize)
    parseLine(lineBuffer, array.data + array.used) // parse CURRENT line
    array.used += 1

  val readElapsed = System.currentTimeMillis() - readStart

  stdio.fprintf(
    stdio.stderr,
    c"reached EOF after %d lines in %d ms\n",
    array.used,
    readElapsed
  )

  val sortStart = System.currentTimeMillis()

  qsort.qsort( // sorts in-place!
    array.data.asInstanceOf[Ptr[Byte]],
    array.used,
    sizeof[NGramData].toLong,
    byCount
  )

  val sortElapsed = System.currentTimeMillis() - sortStart
  stdio.printf(c"sorting done in %d ms\n", sortElapsed)
  val toShow = if array.used <= 20 then array.used else 20 // display top 20 words

  for i <- 0 until toShow do
    stdio.printf(
      c"word %d: %s %d %d %d\n",
      i,
      (array.data + i)._1,
      (array.data + i)._2,
      (array.data + i)._3,
      (array.data + i)._4
    )

  stdio.printf(c"done.\n")

// def parseLineNaive(fd: Ptr[stdio.FILE], array: Ptr[NGramData]): Unit =
//   ??? // TODO
