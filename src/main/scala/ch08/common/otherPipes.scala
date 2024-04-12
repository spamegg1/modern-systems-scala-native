package ch08

import concurrent.{Future, ExecutionContext, Promise}

case class ConcatPipe[T, U](f: T => Seq[U]) extends Pipe[T, U]:
  override def feed(input: T): Unit =
    val output = f(input)
    for
      h <- handlers
      o <- output
    do h.feed(o)

case class OptionPipe[T, U](f: T => Option[U]) extends Pipe[T, U]:
  override def feed(input: T): Unit =
    val output = f(input)
    for
      h <- handlers
      o <- output
    do h.feed(o)

case class AsyncPipe[T, U](f: T => Future[U])(using ec: ExecutionContext)
    extends Pipe[T, U]:
  override def feed(input: T): Unit =
    f(input).map(o => for h <- handlers do h.feed(o))

case class OnComplete[T]()(implicit ec: ExecutionContext) extends Pipe[T, Unit]:
  val promise = Promise[Unit]()
  override def feed(input: T) = ()
  override def done() =
    println("done, completing promise")
    promise.success(())

case class CounterSink[T]() extends Pipe[T, Nothing]:
  var counter = 0
  override def feed(input: T) = counter += 1

case class Tokenizer(separator: String) extends Pipe[String, String]:
  var buffer = ""

  def scan(input: String): Seq[String] =
    println(s"scanning: '$input'")
    buffer = buffer + input
    var o: Seq[String] = Seq()
    while buffer.contains(separator) do
      val space_position = buffer.indexOf(separator)
      val word = buffer.substring(0, space_position)
      o = o :+ word
      buffer = buffer.substring(space_position + 1)
    o

  override def feed(input: String): Unit =
    for
      h <- handlers
      word <- scan(input)
    do h.feed(word)

  override def done(): Unit =
    println(s"done!  current buffer: $buffer")
    for h <- handlers do
      h.feed(buffer)
      h.done()

case class FoldPipe[I, O](init: O)(f: (O, I) => O) extends Pipe[I, O]:
  var accum = init
  override def feed(input: I): Unit =
    accum = f(accum, input)
    for h <- handlers do h.feed(accum)

  override def done(): Unit = for h <- handlers do h.done()
