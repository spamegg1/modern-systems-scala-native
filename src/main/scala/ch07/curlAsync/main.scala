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

  val urls = Seq( // more than 1 URL causes double-free or corrupt size vs prev size
    "https://www.example.com"
    // "https://duckduckgo.com",
    // "https://www.google.com"
  )

  val _ = Zone:
    for url <- urls do // asynchronously make GET requests to multiple websites
      val resp = Curl.startRequest(GET, url)

      resp.onComplete: // this does not trigger!
        case Success(data) =>
          println(s"got response for ${url} - length ${data.body.size}")
          println(s"headers:")
          for h <- data.headers do println(s"request header: $h")
          println(s"body: ${data.body}")
        case Failure(f) => println(s"request failed ${f}")

  EventLoop.run()
  println("done running async event loop with multi curl requests")

// ❯ ./project
// initializing loop
// initializing curl
// initialized multiHandle Ptr@65471d9ac720
// setting up curl socket callback with multiHandle
// setting up curl timer callback with multiHandle
// initializing libuv loop timer with curl timer callback: scala.scalanative.unsafe.CFuncPtr3@5822c462
// uv_timer_init returned 0
// done initializing
// initializing handle Ptr@65471d9afec0 for request 1
// easy_setopt returned 0
// easy_setopt returned 0
// easy_setopt returned 0
// easy_setopt returned 0
// easy_setopt returned 0
// easy_setopt returned 0
// start_timer called with timeout 0 ms
// setting effective timeout to 1
// starting timer
// uv_timer_start returned 0
// done handling messages
// request initialized
// uv_prepare_init returned 0
// in timeout callback
// uv_run returned 0
// socketCB called with action 1
// initializing handle for socket Ptr@d
// uv_poll_init_socket returned 0
// multi_assign returned 0
// starting poll with events 1
// start_timer called with timeout 1 ms
// starting timer
// uv_timer_start returned 0
// done handling messages
// on_timer fired, 1 sockets running
// in timeout callback
// start_timer called with timeout 2 ms
// starting timer
// uv_timer_start returned 0
// done handling messages
// on_timer fired, 1 sockets running
// ready_for_curl fired with status 0 and events 1
// socketCB called with action 4
// stopping poll
// start_timer called with timeout 1 ms
// starting timer
// uv_timer_start returned 0
// done handling messages
// socketCB called with action 2
// initializing handle for socket Ptr@d
// uv_poll_init_socket returned 0
// multi_assign returned 0
// starting poll with events 2
// start_timer called with timeout 0 ms
// setting effective timeout to 1
// starting timer
// uv_timer_start returned 0
// done handling messages
// multi_socket_action 0
// in timeout callback
// start_timer called with timeout 200 ms
// starting timer
// uv_timer_start returned 0
// done handling messages
// on_timer fired, 1 sockets running
// ready_for_curl fired with status 0 and events 2
// socketCB called with action 1
// starting poll with events 1
// multi_socket_action 0
// in timeout callback
// on_timer fired, 1 sockets running
// ready_for_curl fired with status 0 and events 1
// multi_socket_action 0
// ready_for_curl fired with status 0 and events 1
// multi_socket_action 0
// ready_for_curl fired with status 0 and events 1
// multi_socket_action 0
// ready_for_curl fired with status 0 and events 1
// multi_socket_action 0
// ready_for_curl fired with status 0 and events 1
// multi_socket_action 0
// ready_for_curl fired with status 0 and events 1
// multi_socket_action 0
// ready_for_curl fired with status 0 and events 1
// multi_socket_action 0
// ready_for_curl fired with status 0 and events 1
// req 1: got header line of size 1 x 13
// req 1: got header line of size 1 x 13
// req 1: got header line of size 1 x 31
// req 1: got header line of size 1 x 40
// req 1: got header line of size 1 x 37
// req 1: got header line of size 1 x 31
// req 1: got header line of size 1 x 40
// req 1: got header line of size 1 x 46
// req 1: got header line of size 1 x 26
// req 1: got header line of size 1 x 23
// req 1: got header line of size 1 x 14
// req 1: got header line of size 1 x 22
// req 1: got header line of size 1 x 2
// req 1: got data of size 1 x 1256
// socketCB called with action 4
// stopping poll
// start_timer called with timeout 1 ms
// starting timer
// uv_timer_start returned 0
// done handling messages
// multi_socket_action 0
// in timeout callback
// on_timer fired, 0 sockets running
// uv_run returned 0
// done running async event loop with multi curl requests
