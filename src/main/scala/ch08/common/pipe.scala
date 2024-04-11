package ch08

import scalanative.unsigned.{UnsignedRichLong, UnsignedRichInt}
import scalanative.unsafe.*
import scalanative.libc.*, stdlib.*, string.strncpy

import collection.mutable
import util.{Try, Success, Failure}
import concurrent.{Future, ExecutionContext, Promise}

trait Pipe[T, U]:
  val handlers = mutable.Set[Pipe[U, ?]]() // implemented
  def feed(input: T): Unit // unimplemented
  def done(): Unit = for h <- handlers do h.done()
  def addDestination[V](dest: Pipe[U, V]): Pipe[U, V] =
    handlers += dest
    dest
  def map[V](g: U => V): Pipe[U, V] = addDestination(SyncPipe(g))
  def mapConcat[V](g: U => Seq[V]): Pipe[U, V] = addDestination(ConcatPipe(g))
  def mapOption[V](g: U => Option[V]): Pipe[U, V] = addDestination(OptionPipe(g))
  def mapAsync[V](g: U => Future[V])(using ec: ExecutionContext): Pipe[U, V] =
    addDestination(AsyncPipe(g))
  def onComplete(using ec: ExecutionContext): Future[Unit] =
    val sink = OnComplete[U]()
    addDestination(sink)
    sink.promise.future

object Pipe:
  case class PipeSource[I]() extends Pipe[I, I]:
    override def feed(input: I): Unit =
      for h <- handlers do h.feed(input)

  def source[I]: Pipe[I, I] = PipeSource[I]()

case class SyncPipe[T, U](f: T => U) extends Pipe[T, U]:
  override def feed(input: T): Unit =
    val output = f(input)
    for h <- handlers do h.feed(output)

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
