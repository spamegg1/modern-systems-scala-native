package `01inputOutput`

import scalanative.unsafe.*
import scalanative.libc.*
import stdio.*

// every line is of this form:
// ngram TAB year TAB match_count TAB volume_count NEWLINE
// Such as:
// A'Aang_NOUN 1879 45 5
// compile this, then from the root directory, run with:
// ./target/scala-3.2.2/scala-native-out < ./src/main/resources/scala-native/googlebooks-eng-all-1gram-20120701-a
// done. read 86618505 lines
// maximum word count: 470825580 for 'and' @ 2008
// real    13m57,858s (837 seconds)
// user    13m57,015s
// sys     0m0,776s
// This is an 8x speedup from maxNgramNaive
// (just like the book! 4min -> 30sec)

// @main
def maxNgramFast(args: String*): Unit =
  stdio.printf(c"running maxNgramFast\n")

  var maxWord: Ptr[Byte] = stackalloc[Byte](1024)
  val maxCount: Ptr[Int] = stackalloc[Int](sizeof[Int])
  val maxYear: Ptr[Int] = stackalloc[Int](sizeof[Int])

  val lineBuffer: Ptr[Byte] = stackalloc[Byte](1024)
  val tempWord: Ptr[Byte] = stackalloc[Byte](1024)
  val tempCount: Ptr[Int] = stackalloc[Int](sizeof[Int])
  val tempYear: Ptr[Int] = stackalloc[Int](sizeof[Int])
  val tempDocCount: Ptr[Int] = stackalloc[Int](sizeof[Int])

  var linesRead = 0
  !maxCount = 0
  !maxYear = 0

  while stdio.fgets(lineBuffer, 1024, stdio.stdin) != null
  do
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
  stdio.printf(
    c"maximum word count: %d for '%s' @ %d\n",
    !maxCount,
    maxWord,
    !maxYear
  )

def parseAndCompare(
    lineBuffer: CString,
    maxWord: CString,
    tempWord: CString,
    maxWordBufferSize: Int,
    maxCount: Ptr[Int],
    tempCount: Ptr[Int],
    maxYear: Ptr[Int],
    tempYear: Ptr[Int],
    tempDocCount: Ptr[Int]
): Unit =
  val scanResult = stdio.sscanf(
    lineBuffer,
    c"%1023s %d %d %d\n",
    tempWord,
    tempYear,
    tempCount,
    tempDocCount
  )
  if scanResult < 4 then throw new Exception("bad input")
  if !tempCount <= !maxCount then return
  else
    stdio.printf(
      c"saw new max: %s %d occurences at year %d\n",
      tempWord,
      !tempCount,
      !tempYear
    )

    val wordLength = string.strlen(tempWord).toInt

    if wordLength >= maxWordBufferSize - 1 then
      throw new Exception(
        s"length $wordLength exceeded buffer size $maxWordBufferSize"
      )

    string.strncpy(maxWord, tempWord, string.strlen(lineBuffer))
    !maxCount = !tempCount
    !maxYear = !tempYear
