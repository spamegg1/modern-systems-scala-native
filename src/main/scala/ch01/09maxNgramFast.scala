package ch01.maxNgramFast

import scalanative.unsafe.{Ptr, stackalloc, CQuote, CString}
import scalanative.libc.{stdio, string}

// every line is of this form: ngram TAB year TAB match_count TAB volume_count NEWLINE
// Such as: A'Aang_NOUN 1879 45 5
// compile this, then run with:
// ./path/to/binary/file < ./src/main/resources/googlebooks-eng-all-1gram-20120701-a
// done. read 86618505 lines
// maximum word count: 470825580 for 'and' @ 2008
// real    13m57,858s (837 seconds)
// user    13m57,015s
// sys     0m0,776s
// This is an ~8x speedup from maxNgramNaive (~2 hours -> ~15 mins)
// (just like the book! 4min -> 30sec)

@main
def maxNgramFast(args: String*): Unit =
  stdio.printf(c"running maxNgramFast\n")

  // to store the results of our search.
  var maxWord: Ptr[Byte] = stackalloc[Byte](1024) // big enough to be safe
  val maxCount: Ptr[Int] = stackalloc[Int](1)
  val maxYear: Ptr[Int] = stackalloc[Int](1)

  // to be used in sscanf, temporary storage.
  val lineBuffer: Ptr[Byte] = stackalloc[Byte](1024) // we know lines in file are short.
  val tempWord: Ptr[Byte] = stackalloc[Byte](1024) // this matches maxWord.
  val tempCount: Ptr[Int] = stackalloc[Int](1)
  val tempYear: Ptr[Int] = stackalloc[Int](1)
  val tempDocCount: Ptr[Int] = stackalloc[Int](1)

  // initialize values
  var linesRead = 0
  !maxCount = 0
  !maxYear = 0

  // we will feed the file into stdin, then into the program.
  while stdio.fgets(lineBuffer, 1024, stdio.stdin) != null do
    linesRead += 1
    parseAndCompare(
      lineBuffer,
      maxWord,
      tempWord,
      1024,
      maxCount,
      tempCount,
      maxYear,
      tempYear,
      tempDocCount
    )

  stdio.printf(c"done. read %d lines\n", linesRead)
  stdio.printf(c"maximum word count: %d for '%s' @ %d\n", !maxCount, maxWord, !maxYear)

def parseAndCompare(
    lineBuffer: CString, // one line of the file, read from stdin.
    maxWord: CString, // pointer from @main
    tempWord: CString, // pointer from @main
    maxWordBufferSize: Int, // 1024
    maxCount: Ptr[Int], // pointers to hold the results
    tempCount: Ptr[Int],
    maxYear: Ptr[Int],
    tempYear: Ptr[Int],
    tempDocCount: Ptr[Int]
): Unit =
  val scanResult = stdio.sscanf(
    lineBuffer,
    c"%1023s %d %d %d\n", // word, year, wordCount, volumeCount
    tempWord,
    tempYear,
    tempCount,
    tempDocCount
  )
  if scanResult < 4 then throw Exception("bad input")
  if !tempCount <= !maxCount then () // not a new max, so do nothing.
  else
    stdio.printf(
      c"saw new max: %s %d occurences at year %d\n",
      tempWord,
      !tempCount,
      !tempYear
    )

  val wordLength = string.strlen(tempWord).toInt
  if wordLength >= maxWordBufferSize - 1 then
    throw Exception(s"length $wordLength exceeded buffer size $maxWordBufferSize")

  // update max values
  string.strncpy(maxWord, tempWord, string.strlen(lineBuffer))
  !maxCount = !tempCount
  !maxYear = !tempYear
