/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
import scalanative.native._
import Curl._
import LibCurl._
import stdlib._
import stdio._

object curlBasic {

    def writeData(ptr: Ptr[Byte], size: CSize, nmemb: CSize, data: Ptr[CurlBuffer]): CSize = {
        val len = stackalloc[Double]
        !len = 0
        println(easy_getinfo(data.cast[Ptr[Byte]], CONTENTLENGTHDOWNLOADT, len))
        println(s"got data of size ${size} x ${nmemb}, body length ${!len}")
        return size * nmemb
    }

    def writeHeader(ptr: Ptr[Byte], size: CSize, nmemb: CSize, data: Ptr[CurlBuffer]): CSize = {
        val len = stackalloc[Double]
        !len = 0
        println(easy_getinfo(data.cast[Ptr[Byte]], CONTENTLENGTHDOWNLOADT, len))
        val byteSize = size * nmemb
        printf(c"got header line of size %d, body length %f: ", !len, byteSize)
        fwrite(ptr, size, nmemb, stdout)
        return byteSize
    }

    val writeCB = CFunctionPtr.fromFunction4(writeData)
    val headerCB = CFunctionPtr.fromFunction4(writeHeader)

    def main(args:Array[String]):Unit = {
        println("hello world")
        println("initializing")
        global_init(1)
        println("initialized, creating handle")
        val curl = easy_init()
        println("initialized")

        val bodyResp = malloc(sizeof[CurlBuffer]).cast[Ptr[CurlBuffer]]
        !bodyResp._1 = malloc(4096).cast[CString]
        !bodyResp._2 = 0
        val headersResp = malloc(sizeof[CurlBuffer]).cast[Ptr[CurlBuffer]]
        !headersResp._1 = malloc(4096).cast[CString]
        !headersResp._2 = 0
        println(easy_setopt(curl, URL, c"http://www.example.com"))
        println(easy_setopt(curl, WRITECALLBACK, writeCB))
        println(easy_setopt(curl, WRITEDATA, curl))
        println(easy_setopt(curl, HEADERCALLBACK, headerCB))
        println(easy_setopt(curl, HEADERDATA, curl))

        val multi = multi_init()
        val handles = stackalloc[Int]
        !handles = 1
        println("multi_add_handle", multi_add_handle(multi, curl))
        while (!handles > 0) {
            val poll_result = multi_perform(multi, handles)
            println("multi_perform", poll_result, "handles:", !handles )
            if (!handles > 0) { Thread.sleep(100) }
        }

        println("cleaning up")
        easy_cleanup(curl)
        multi_cleanup(multi)
        println("global cleanup...")
        global_cleanup()
        println("done")
    }
}
