/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
import scala.scalanative.unsafe._
import scala.scalanative.libc._

import argonaut._, Argonaut._
import scala.concurrent.{Future, ExecutionContext}

object Main {
  import LibUVConstants._, LibUV.uv_run, ServiceHelpers._
  implicit val ec = EventLoop

  def main(args:Array[String]):Unit = {
    Service()
      .getAsync("/async/") { r => Future(OK(
        Map("asyncMessage" -> s"got (async routed) request $r")
      ))}
      .get("/") { r => OK(
        Map("message" -> s"got (routed) request $r")
      )}
      .run(9999)
    uv_run(EventLoop.loop, UV_RUN_DEFAULT)
    println("done")
  }
}

object ServiceHelpers {
  def OK[T](body:T, headers:Map[String,String] = Map.empty)
           (implicit e:EncodeJson[T]):Response[String] = {
    val b = body.asJson.nospaces
    Response(200,"OK",headers,b)
  }
}

case class Service(routes:Seq[Route] = Seq.empty)
                  (implicit ec:ExecutionContext) {
  def dispatch(req:Request[String]):Route = {
    for (route <- routes) {
      if (req.method == route.method && req.url.startsWith(route.path)) {
        println(s"matched route ($route)")
        return route
      }
    }
    throw new Exception("no match!")
  }

  def run(port:Int) = {
    Server.init(port, this.dispatch)
  }
  def get(path:String)(h:Request[String] => Response[String]):Service = {
    return Service(this.routes :+ SyncRoute("GET",path,h))
  }
  def getAsync(path:String)(h:Request[String] => Future[Response[String]]):
    Service = {
    return Service(this.routes :+ AsyncRoute("GET",path,h))
  }
  def post[I,O](path:String)(h:Request[I] => Response[O])
      (implicit d:DecodeJson[I], e:EncodeJson[O]):Service = {
    val handler = (r:Request[String]) => {
      val parsedRequest = Parse.decodeOption[I](r.body) match {
        case Some(i) =>
          Request[I](r.method,r.url,r.headers,i)
      }
      val resp = h(parsedRequest)
      Response[String](resp.code, resp.description,
                       resp.headers, resp.body.asJson.nospaces)
    }
    return Service(this.routes :+ SyncRoute("POST",path,handler))
  }

  def postAsync[I,O](path:String)(h:Request[I] => Future[Response[O]])
      (implicit d:DecodeJson[I], e:EncodeJson[O]):Service = {
    val handler = (r:Request[String]) => {
      val parsedRequest = Parse.decodeOption[I](r.body) match {
        case Some(i) =>
          Request[I](r.method,r.url,r.headers,i)
      }
      h(parsedRequest).map { resp =>
        Response[String](resp.code, resp.description,
                         resp.headers, resp.body.asJson.nospaces)
      }
    }
    return Service(this.routes :+ AsyncRoute("POST",path,handler))
  }
}
