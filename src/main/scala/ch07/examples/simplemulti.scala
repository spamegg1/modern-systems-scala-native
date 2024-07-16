package ch07
package examples

import scalanative.libc.*, stdlib.*, stdio.*
import scalanative.unsafe.*
import scalanative.unsigned.UnsignedRichInt
import Curl.*

// This code is not meant to compile and run, it's conceptual pseudocode.
// I just made up some stuff to make it compile.

object curlBasic:
  import LibCurl.*, LibCurlConstants.*
  def writeData(ptr: Ptr[Byte], size: CSize, nmemb: CSize, data: Ptr[CurlBuffer]): CSize =
    val len = stackalloc[Double](1)
    !len = 0.0
    println(
      easy_getinfo(
        data.asInstanceOf[Ptr[Byte]],
        CONTENTLENGTHDOWNLOADT,
        len.asInstanceOf[Ptr[Byte]]
      )
    )
    println(s"got data of size ${size} x ${nmemb}, body length ${!len}")
    size * nmemb

  def writeHeader(
      ptr: Ptr[Byte],
      size: CSize,
      nmemb: CSize,
      data: Ptr[CurlBuffer]
  ): CSize =
    val len = stackalloc[Double]()
    !len = 0
    println(
      easy_getinfo(
        data.asInstanceOf[Ptr[Byte]],
        CONTENTLENGTHDOWNLOADT,
        len.asInstanceOf[Ptr[Byte]]
      )
    )
    val byteSize = size * nmemb
    printf(c"got header line of size %d, body length %f: ", !len, byteSize)
    fwrite(ptr, size, nmemb, stdout)
    byteSize

  val writeCB = CFuncPtr4.fromScalaFunction(writeData)
  val headerCB = CFuncPtr4.fromScalaFunction(writeHeader)

  @main
  def run(args: String*): Unit =
    println("hello world")
    println("initializing")
    global_init(1)
    println("initialized, creating handle")
    val curl = easy_init()
    println("initialized")

    val bodyResp = malloc(sizeof[CurlBuffer]).asInstanceOf[Ptr[CurlBuffer]]
    !bodyResp._1 = malloc(4096).asInstanceOf[CChar]
    bodyResp._2 = 0.toUSize
    val headersResp = malloc(sizeof[CurlBuffer]).asInstanceOf[Ptr[CurlBuffer]]
    !headersResp._1 = malloc(4096).asInstanceOf[CChar]
    headersResp._2 = 0.toUSize
    println(curl_easy_setopt(curl, URL, c"http://www.example.com"))
    // println(curl_easy_setopt(curl, WRITECALLBACK, writeCB))
    println(curl_easy_setopt(curl, WRITEDATA, curl))
    // println(curl_easy_setopt(curl, HEADERCALLBACK, headerCB))
    println(curl_easy_setopt(curl, HEADERDATA, curl))

    val multi = multi_init()
    val handles = stackalloc[Int]()
    !handles = 1
    println(s"multi_add_handle: ${multi_add_handle(multi, curl)}")
    while !handles > 0 do
      val pollResult = multi_perform(multi, handles)
      println(s"multi_perform: $pollResult, handles: ${!handles}")
      if !handles > 0 then Thread.sleep(100)

    println("cleaning up")
    easy_cleanup(curl)
    multi_cleanup(multi)
    println("global cleanup...")
    global_cleanup()
    println("done")
