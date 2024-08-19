package ch08

import scalanative.unsigned.{UnsignedRichLong, UnsignedRichInt}
import scalanative.unsafe.*
import scalanative.libc.*, stdlib.*, string.strncpy

import collection.mutable
import scala.util.{Try, Success, Failure}
import concurrent.{Future, ExecutionContext, Promise}

trait Pipe[T, U]:
  def feed(input: T): Unit // unimplemented
  val handlers = mutable.Set[Pipe[U, ?]]() // the rest is implemented
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
