package ch09
package lmdbWeb

import scalanative.unsigned.{UnsignedRichLong, UnsignedRichInt}
import scala.scalanative.unsafe.*
import scala.scalanative.libc.{stdio, stdlib, string}
import argonaut.{Argonaut, EncodeJson, DecodeJson}
import Argonaut.{ToJsonIdentity, StringToParseWrap}

object LMDB:
  import LmdbImpl.*

  def open(path: CString): Env =
    val envPtr = stackalloc[Env](1)
    check(mdb_env_create(envPtr), "mdb_env_create")
    val env = !envPtr
    // Unix permissions for 0644 (read/write)
    check(mdb_env_open(env, path, 0, 420), "mdb_env_open")
    env

  def getJson[T](env: Env, key: String)(using dec: DecodeJson[T]): T =
    val value = getString(env, key)
    value.decodeOption[T].get

  def putJson[T](env: Env, key: String, value: T)(using enc: EncodeJson[T]): Unit =
    val valueString = value.asJson.nospaces
    putString(env, key, valueString)

  def getString(env: Env, key: String): String =
    Zone: // implicit z => // 0.5
      val k = toCString(key)
      fromCString(get(env, k))

  def putString(env: Env, key: String, value: String): Unit =
    Zone: // implicit z => // 0.5
      val k = toCString(key)
      val v = toCString(value)
      put(env, k, v)

  def put(env: Env, key: CString, value: CString): Unit =
    val databasePtr = stackalloc[DB](1)
    val transactionPtr = stackalloc[Transaction](1)

    check(mdb_txn_begin(env, null, 0, transactionPtr), "mdb_txn_begin")
    val transaction = !transactionPtr

    check(mdb_dbi_open(transaction, null, 0, databasePtr), "mdb_dbi_open")
    val database = !databasePtr

    val k = stackalloc[Key](1)
    k._1 = string.strlen(key).toLong + 1.toLong
    k._2 = key

    val v = stackalloc[Value](1)
    v._1 = string.strlen(value).toLong + 1.toLong
    v._2 = value

    check(mdb_put(transaction, database, k, v, 0), "mdb_put")
    check(mdb_txn_commit(transaction), "mdb_txn_commit")

  def get(env: Env, key: CString): CString =
    val databasePtr = stackalloc[DB](1)
    val transactionPtr = stackalloc[Transaction](1)

    check(mdb_txn_begin(env, null, 0, transactionPtr), "mdb_txn_begin")
    val transaction = !transactionPtr

    check(mdb_dbi_open(transaction, null, 0, databasePtr), "mdb_dbi_open")
    val database = !databasePtr

    val rKey = stackalloc[Key](1)
    rKey._1 = string.strlen(key).toLong + 1.toLong
    rKey._2 = key

    val rValue = stackalloc[Value](1)
    check(mdb_get(transaction, database, rKey, rValue), "mdb_get")

    stdio.printf(c"key: %s value: %s\n", rKey._2, rValue._2)
    val output = stdlib.malloc(rValue._1) // 0.5
    string.strncpy(output, rValue._2, rValue._1.toUSize) // 0.5
    check(mdb_txn_abort(transaction), "mdb_txn_abort")
    output

  def check(result: Int, label: String): Unit =
    if result != 0 then throw Exception(s"bad LMDB call: $label returned $result")
    else println(s"$label returned $result")
