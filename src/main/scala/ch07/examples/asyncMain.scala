package ch07
package examples

import scalanative.unsafe.*
import util.{Success, Failure}
import concurrent.ExecutionContext

@main
def asyncMain(args: String*): Unit =
  if args.length == 0 then
    println("usage: ./curl-out https://whatever.url/you/want?to=fetch")

  println("initializing loop")
  given ExecutionContext = EventLoop // ch07.common.loop.scala

  val resp = Zone:
    for arg <- args do
      val url: CString = toCString(arg)
      val resp = Curl.get(url)

      resp.onComplete:
        case Success(body) =>
          println(s"got back response for ${arg} - body of length ${body.size}")
          println(body.substring(0, 80))
          println("...")
          println(body.substring(body.size - 80))
        case Failure(f) => println(s"request failed $f")
  EventLoop.run()
  println("loop done, cleaning up")
  Curl.cleanupRequests // Curl.cleanup() // ???
  println("done")
