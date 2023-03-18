// @main
def testLinkIssue: Unit =
  val fileName = "hello.txt"
  for line <- io.Source.fromResource(fileName).getLines
  do println(line)
