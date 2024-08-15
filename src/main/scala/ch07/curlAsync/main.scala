package ch07
package curlAsync

import scala.scalanative.unsafe.Zone
import scala.util.{Success, Failure}
import LibCurlConstants.GET
import scala.concurrent.ExecutionContext

@main
def run: Unit = // I got rid of command line arguments, easier to run.
  println("initializing loop")
  given ExecutionContext = EventLoop // used by onComplete

  val urls = Seq(
    "https://www.example.com",
    "https://duckduckgo.com",
    "https://www.google.com"
  )

  val resp = Zone:
    for url <- urls do // asynchronously make GET requests to multiple websites
      val resp = Curl.startRequest(GET, url)

      resp.onComplete:
        case Success(data) =>
          println(s"got response for ${url} - length ${data.body.size}")
          println(s"headers:")
          for h <- data.headers do println(s"request header: $h")
          println(s"body: ${data.body}")
        case Failure(f) => println(s"request failed ${f}")

  EventLoop.run()
  println("done running async event loop with multi curl requests")
