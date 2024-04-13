package ch09
package lmdbWeb

import scalanative.unsigned.{UnsignedRichLong, UnsignedRichInt}
import scalanative.unsafe.*
import scalanative.libc.{stdlib, string}, stdlib.malloc
import argonaut.{Argonaut, EncodeJson, DecodeJson}
import Argonaut.{StringToParseWrap, ToJsonIdentity}

import ch03.http.{HttpRequest, HttpResponse}

val addPatn = raw"/add/([^/]+)/([^/]+)".r
val fetchPatn = raw"/fetch/([^/]+)".r
val listPatn = raw"/list/([^/]+)".r
val listOffsetPatn = raw"/list/([^/]+)?offset=([^/]+)".r // ??? unused

@main
def lmbdWebMain1(args: String*): Unit =
  val env = LMDB.open(c"./db")
  Server.serveHttp(
    8080,
    request =>
      request.uri match
        case addPatn(setKey, key) =>
          val data = parseBody[Map[String, String]](request)
          val set = LMDB.getJson[List[String]](env, setKey)
          val newSet = key :: set
          LMDB.putJson(env, setKey, newSet)
          LMDB.putJson(env, key, data)
          makeResponse("OK")
        case fetchPatn(key) =>
          val item = LMDB.getJson[Map[String, String]](env, key)
          makeResponse(item)
        case listPatn(setKey) =>
          val set = LMDB.getJson[List[String]](env, setKey)
          makeResponse(set)
        case _ => makeResponse("no route match\n")
  )

def parseBody[T](request: HttpRequest)(using dec: DecodeJson[T]): T =
  request.body.decodeOption[T].get

def makeResponse[T](resp: T)(using enc: EncodeJson[T]): HttpResponse =
  val respString = resp.asJson.spaces2
  val size = respString.size.toString
  HttpResponse(200, Map("Content-Length" -> size), respString)

@main
def lmbdWebMain2(args: String*): Unit =
  val env = LMDB.open(c"./db")
  Server.serveHttp(
    8080,
    request =>
      request.uri match
        case addPatn(setKey, key) =>
          val data = parseBody[Map[String, String]](request)
          val set = LMDB.getJson[List[String]](env, setKey)
          val newSet = key :: set
          LMDB.putJson(env, setKey, newSet)
          LMDB.putJson(env, key, data)
          makeResponse("OK")
        case fetchPatn(key) =>
          val item = LMDB.getJson[Map[String, String]](env, key)
          makeResponse(item)
        case listPatn(setKey) =>
          val set = LMDB.getJson[List[String]](env, setKey)
          makeResponse(set)
  )
