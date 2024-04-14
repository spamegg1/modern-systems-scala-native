package ch10

import util.boundary, boundary.break
import concurrent.{Future, ExecutionContext}
import argonaut.{Argonaut, EncodeJson, DecodeJson, Parse}
import Argonaut.ToJsonIdentity

import ch07.LibUVConstants.*, ch07.LibUV.uv_run, ServiceHelpers.*

given ec: ExecutionContext = EventLoop // changed implicit val

@main
def libuvService(args: String*): Unit =
  Service()
    .getAsync("/async/"): r =>
      Future(OK(Map("asyncMessage" -> s"got (async routed) request $r")))
    .get("/"): r =>
      OK(Map("message" -> s"got (routed) request $r"))
    .run(9999)
  uv_run(EventLoop.loop, UV_RUN_DEFAULT)
  println("done")

object ServiceHelpers:
  def OK[T](
      body: T,
      headers: Map[String, String] = Map.empty
  )(using e: EncodeJson[T]): Response[String] =
    Response(200, "OK", headers, body.asJson.nospaces)

case class Service(routes: Seq[Route] = Seq.empty)(using ec: ExecutionContext):
  def dispatch(req: Request[String]): Route =
    boundary:
      for route <- routes do
        if req.method == route.method && req.url.startsWith(route.path) then
          println(s"matched route ($route)")
          break(route)
    throw Exception("no match!")

  def run(port: Int) = Server.init(port, dispatch)

  def get(path: String)(h: Request[String] => Response[String]): Service =
    Service(this.routes :+ SyncRoute("GET", path, h))

  def getAsync(path: String)(h: Request[String] => Future[Response[String]]): Service =
    Service(this.routes :+ AsyncRoute("GET", path, h))

  def post[I, O](path: String)(
      h: Request[I] => Response[O]
  )(using d: DecodeJson[I], e: EncodeJson[O]): Service =
    val handler = (r: Request[String]) =>
      val parsedRequest = Parse.decodeOption[I](r.body) match
        case Some(i) => Request[I](r.method, r.url, r.headers, i)
        case None    => ???

      val resp = h(parsedRequest)
      Response[String](
        resp.code,
        resp.description,
        resp.headers,
        resp.body.asJson.nospaces
      )
    Service(this.routes :+ SyncRoute("POST", path, handler))

  def postAsync[I, O](path: String)(
      h: Request[I] => Future[Response[O]]
  )(using d: DecodeJson[I], e: EncodeJson[O]): Service =
    val handler = (r: Request[String]) =>
      val parsedRequest = Parse.decodeOption[I](r.body) match
        case Some(i) => Request[I](r.method, r.url, r.headers, i)
        case None    => ???

      h(parsedRequest).map: resp =>
        Response[String](
          resp.code,
          resp.description,
          resp.headers,
          resp.body.asJson.nospaces
        )
    Service(this.routes :+ AsyncRoute("POST", path, handler))
