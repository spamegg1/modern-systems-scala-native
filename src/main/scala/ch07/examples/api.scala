package ch07
package examples

// This code is not meant to compile and run, it's conceptual pseudocode.
// I just made up some stuff to make it compile.

import concurrent.Future
import concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}

enum Method: // made up!
  case GET, POST, PUT, DELETE
import Method.*

case class Request( // made up!
    method: Method, // GET, POST, PUT, etc.
    uri: String, // e.g. www.pragprog.com
    headers: Seq[String],
    body: Option[String]
)
case class Response( // made up!
    code: Int, // 200, 301, 404, 500, etc.
    headers: Seq[String],
    body: String
)

object HTTPAPI:
  private def makeRequest(req: Request): Future[Response] = ???

  def get(uri: String, headers: Seq[String] = Seq()): Future[Response] =
    makeRequest(Request(GET, uri, headers, None))

  def post(uri: String, headers: Seq[String] = Seq(), body: String): Future[Response] =
    makeRequest(Request(POST, uri, headers, Some(body)))

  def put(uri: String, headers: Seq[String] = Seq(), body: String): Future[Response] =
    makeRequest(Request(PUT, uri, headers, Some(body)))

@main
def libuvApi(args: String*): Unit =
  HTTPAPI
    .get("http://example.com") // made up!
    .onComplete: attempt =>
      attempt match
        case Success(response) =>
          println(s"got back response code ${response.code}")
          println(s"response body: ${response.body}")
        case Failure(ex) => throw ex
