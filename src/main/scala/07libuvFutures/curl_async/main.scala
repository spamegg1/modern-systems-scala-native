package `07curlAsync`

import scala.scalanative.unsafe.*
import scala.scalanative.libc.*
import scala.util.{Success, Failure}
import LibCurlConstants.*

// @main
def curlAsync(args: String*): Unit =
  if args.length == 0 then
    println("usage: ./curl-out https://www.example.com")
    ???

  println("initializing loop")
  implicit val loop = EventLoop

  val resp = Zone { // implicit z => // 0.5
    for arg <- args do
      val url = arg
      val resp = Curl.startRequest(GET, url)

      resp.onComplete {
        case Success(data) =>
          println(s"got response for ${arg} - length ${data.body.size}")
          println(s"headers:")
          for h <- data.headers do println(s"request header: $h")
          println(s"body: ${data.body}")

        case Failure(f) => println(s"request failed ${f}")
      }
  }

  loop.run()
  println("done")
