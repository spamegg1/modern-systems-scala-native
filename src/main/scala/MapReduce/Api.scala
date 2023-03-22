/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
object PipeStream {
  def makeStream[T]
  def fromSeq[T](s:Seq[T])(implicit e:EncodeJSON[T]): PipeStream[T]
}

case class PipeStream[T](inFd:Ptr[File], outFd:Ptr[File]) {
  def map[U](f: T -> U)(implicit d:DecodeJSON[T], e:EncodeJSON[U]): PipeStream[U]
  def reduce[U](init:U, accum: (T, U) -> U): U
}

def map[I,O](list:List[I], f:I => O):List[O] = 
  for (i <- list) yield 
    f(i)

def reduce[I](list:List[I], f:(I,O) => O, init:O):O = {
  var accum = init
  for (i <- list)
    accum = accum(i,accum)
  accum
}

def readAHugeFile(filename:String) = ???

val files = Seq("big_file_1", "big_file_2", "big_file_3", "big_file_4")

for (filename <- files) {
  readAHugeFile(filename)
}

val process_ids = files.map { filename =>
  doFork { 
    readAHugeFile(filename)
  }
}

for (pid <- process_ids) {
  await(pid)
}
