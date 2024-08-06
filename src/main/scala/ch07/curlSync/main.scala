package ch07
package curlSync

@main
def run(args: String*): Unit =
  println("initializing")
  LibCurl.global_init(1)
  val response = CurlBasic.getSync(args(0))
  println(s"done. got response: $response")
  println("global cleanup...")
  LibCurl.global_cleanup()
  println("done")
