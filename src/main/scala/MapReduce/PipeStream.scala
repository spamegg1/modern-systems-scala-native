/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
import argonaut._, Argonaut._

case class PipeStream[T](inFd:Int, outFd:Int) { // Should probably be Ptr[FILE]
  def map[U](f: T -> U)(implicit d:DecodeJSON[T], implicit e:EncodeJSON[U]): PipeStream[U] = {
    val outStream = makeStream[U]
    val buffer = malloc(1024)
    doFork { () => 
      while (fgets(inFd, buffer, 1024) != null) {
        val encoded = fromCString(buffer)
        val item = encoded.decodeOption[T].map {
          case Success(t:T) => 
            val result = f(t) // catch errors
            writeItem(result)
          case Failure =>
            println(s"encountered error parsing '%s'")
        }
      }
    } 
    return outStream
  }

  def fold[U](init:U, accum: (T, U) -> U): U = {
    val outStream = makeStream[T]
    val buffer = malloc(1024)
    var partialResult = init
    while (fgets(inFd, buffer, 1024) != null) {
      val encoded = fromCString(buffer)
      val item = encoded.decodeOption[T].map {
        case Success(t:T) =>
          partialResult = accum(t,partialResult)
      }
    }
    val finalResult = partialResult
    return finalResult
  }

  def writeItem(item:T)(implicit e:EncodeJSON[T]):Unit = Zone {
    val encoded = item.asJson.spaces2
    fprintf(outFd, c"%s\n", encoded.toCString)
  } 
}

object PipeStream {
  def makeStream[T] = {
    val pipe_array = stackalloc[Int](2)
    pipe(pipe_array) //CHECK
    output_pipe = pipe_array(1)
    input_pipe = pipe_array(0)
    return PipeStream(input_pipe, output_pipe)
  }

  def pipeSeq[T](s:Seq[T])(implicit e:EncodeJSON[T]): PipeStream[T] = {
    val outStream = makeStream[T]
    doFork { () => 
      for (item <- s) {
        outStream.writeItem(item)
      }
    }
  }

  def pipeSeqParallel[T](s:Seq[T])(implicit e:EncodeJSON[T]): Seq[PipeStream[T]] = {
    val outStreamsWithData = s.map { item => (item, makeStream[T])}
    for ( (item, stream) <- outStreamsWithData) {
      doFork { () =>
        stream.writeItem(item)
      }
    }
    return outStreamsWithData.map { _._2 }
  }

  def mergePipes[T](inStreams:Seq[PipeStream[T]])(implicit d:DecodeJSON[T], e:EncodeJSON[T]): PipeStream[T] = {
    val outStream = makeStream[T]
    val buffer = malloc(1024)
    var activeStreams = inStreams
    var pollFds = makePollFds(activeStreams)
    for (s, i <- inStreams.zipWithIndex) {
      pollFds(i)._1 = filenum(s.inFd)
      pollFds(i)._2 = POLLIN
      pollFds(i)._3 = 0
    }
    doFork { () => 
      while (activeStreams.size > 0) {
        poll(pollFds)
        for (s, i <- activeStreams.zipWithIndex) {
          if (pollFds(i)._3 & POLLIN) {
            // Skip Decode here
            fgets(buffer, s.inFd, 1024)
            fprintf(outStream.outFd, c"%s\n", buffer)
          }
          if (pollFds(i)._3 & POLLHUP) {
            close(filenum(s.inFd))
            // Check if all FD's are closed.
            activeStreams = activeStreams.remove(s)
            free(pollFds)
            if (activeStreams.size > 0) {
              pollFds = makePollFds(activeStreams)
            } 
          }
        }
      }
      0
    }
  }

  def makePollFds[T](inStreams:Seq[PipeStream[T]]):Ptr[PollFD] = {
    val pollFds = malloc(sizeof[PollFD] * inStreams.size)
    for (s, i <- inStreams.zipWithIndex) {
      pollFds(i)._1 = filenum(s.inFd)
      pollFds(i)._2 = POLLIN | POLLHUP
      pollFds(i)._3 = 0
    }
    return pollFds
  }
}

object main {
  def main(args:Array[String]): Unit = {
    println("hello json world")
    val listData = List(1,2,3)
    val listEncoded = listData.asJson.nospaces
    println(s"I encoded the list $listData to json: '$listEncoded'")
    val listDecoded = listEncoded.decodeOption[List[Int]].getOrElse(Nil)
    println(s"I read back the list $listDecoded")

    val mapData = Map( "a" -> 1, "b" -> 2)
    val mapEncoded = mapData.asJson.nospaces
    println(s"I encoded the map $mapData to json: '$mapEncoded'")
    val mapDecoded = mapEncoded.decodeOption[Map[String,Int]].getOrElse(Map())
    println(s"I read back the map $mapDecoded")
  }
}

type PollFD = CStruct3[Int, Short, Short]