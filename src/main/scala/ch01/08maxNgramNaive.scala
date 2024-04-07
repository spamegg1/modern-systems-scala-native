package ch01.maxNgramNaive

// every line is of this form: ngram TAB year TAB match_count TAB volume_count NEWLINE
// Such as: A'Aang_NOUN 1879 45 5
// compile this, run with:
// ./path/to/binary/file < ./src/main/resources/googlebooks-eng-all-1gram-20120701-a
// it took 7316 seconds.
// max count: and, 2008; 470825580 occurrences
// 7315929 ms elapsed total.

@main
def maxNgramNaive(args: String*): Unit =
  var max = 0
  var maxWord = ""
  var maxYear = 0
  println("reading from STDIN")
  val readStart = System.currentTimeMillis()
  var linesRead = 0

  // we will feed the input file into stdin, then read it from there.
  for line <- scala.io.Source.stdin.getLines do
    val splitFields = line.split("\\s+")
    if splitFields.size != 4 then throw Exception("Parse Error")

    val word = splitFields(0)
    val year = splitFields(1).toInt
    val count = splitFields(2).toInt

    if count > max then
      println(s"found new max: $word $count $year")
      max = count
      maxWord = word
      maxYear = year

    linesRead += 1

    if linesRead % 5000000 == 0 then
      val elapsedNow = System.currentTimeMillis() - readStart
      println(s"read $linesRead lines in $elapsedNow ms")

  val readDone = System.currentTimeMillis() - readStart
  println(s"max count: ${maxWord}, ${maxYear}; ${max} occurrences")
  println(s"$readDone ms elapsed total.")
