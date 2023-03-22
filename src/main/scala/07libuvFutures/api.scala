/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
object HTTPAPI {
  private def makeRequest(Request):Future[Response]

  def get(uri:String, headers:Seq[String] = Seq()): Future[Response] = 
    makeRequest(Request(GET,uri,headers,None)

  def post(uri:String, headers:Seq[String] = Seq(),
           body:String): Future[Response] = 
    makeRequest(Request(POST,uri,headers,Some(body))

  def put(uri:String, headers:Seq[String] = Seq(),
          body:String): Future[Response] =
    makeRequest(Request(PUT,uri,headers,Some(body))
}

object Main {
  def main(args:Array[String]):Unit = {
    val getRequest = get(some_uri)
    getRequest.onComplete { response =>
      println(s"got back response code ${response.code}")
      println(s"response body: ${response.body}")
    }
  }
}
```