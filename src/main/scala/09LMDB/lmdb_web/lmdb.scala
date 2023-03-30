package `09lmdbWeb`

import scalanative.unsigned.{UnsignedRichLong, UnsignedRichInt}
import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.libc.*
import argonaut.*
import Argonaut.*

object LMDB:
  import lmdb_impl.*

  def open(path: CString): Env =
    val envPtr = stackalloc[Env](sizeof[Env])
    check(mdb_env_create(envPtr), "mdb_env_create")
    val env = !envPtr
    // Unix permissions for 0644 (read/write)
    check(mdb_env_open(env, path, 0, 420), "mdb_env_open")
    env

  def getJson[T](env: Env, key: String)(implicit dec: DecodeJson[T]): T =
    val value = getString(env, key)
    value.decodeOption[T].get

  def putJson[T](env: Env, key: String, value: T)(implicit
      enc: EncodeJson[T]
  ): Unit =
    val valueString = value.asJson.nospaces
    putString(env, key, valueString)

  def getString(env: Env, key: String): String =
    Zone { implicit z =>
      val k = toCString(key)
      fromCString(get(env, k))
    }

  def putString(env: Env, key: String, value: String): Unit =
    Zone { implicit z =>
      val k = toCString(key)
      val v = toCString(value)
      put(env, k, v)
    }

  def put(env: Env, key: CString, value: CString): Unit =
    val databasePtr = stackalloc[DB](sizeof[DB])
    val transactionPtr = stackalloc[Transaction](sizeof[Transaction])

    check(mdb_txn_begin(env, null, 0, transactionPtr), "mdb_txn_begin")
    val transaction = !transactionPtr

    check(mdb_dbi_open(transaction, null, 0, databasePtr), "mdb_dbi_open")
    val database = !databasePtr

    val k = stackalloc[Key](sizeof[Key])
    k._1 = string.strlen(key).toLong + 1.toLong
    k._2 = key
    val v = stackalloc[Value](sizeof[Value])
    v._1 = string.strlen(value).toLong + 1.toLong
    v._2 = value

    check(mdb_put(transaction, database, k, v, 0), "mdb_put")
    check(mdb_txn_commit(transaction), "mdb_txn_commit")

  def get(env: Env, key: CString): CString =
    val databasePtr = stackalloc[DB](sizeof[DB])
    val transactionPtr = stackalloc[Transaction](sizeof[Transaction])

    check(mdb_txn_begin(env, null, 0, transactionPtr), "mdb_txn_begin")
    val transaction = !transactionPtr

    check(mdb_dbi_open(transaction, null, 0, databasePtr), "mdb_dbi_open")
    val database = !databasePtr

    val rKey = stackalloc[Key](sizeof[Key])
    rKey._1 = string.strlen(key).toLong + 1.toLong
    rKey._2 = key
    val rValue = stackalloc[Value](sizeof[Value])

    check(mdb_get(transaction, database, rKey, rValue), "mdb_get")

    stdio.printf(c"key: %s value: %s\n", rKey._2, rValue._2)
    val output = stdlib.malloc(rValue._1.toULong)
    string.strncpy(output, rValue._2, rValue._1.toULong)
    check(mdb_txn_abort(transaction), "mdb_txn_abort")
    output

  def check(result: Int, label: String): Unit =
    if result != 0 then
      throw new Exception(s"bad LMDB call: $label returned $result")
    else println(s"$label returned $result")

@link("lmdb")
@extern
object lmdb_impl:
  type Env = Ptr[Byte]
  type Transaction = Ptr[Byte]
  type DB = UInt
  type Key = CStruct2[Long, Ptr[Byte]]
  type Value = CStruct2[Long, Ptr[Byte]]
  def mdb_env_create(env: Ptr[Env]): Int = extern
  def mdb_env_open(env: Env, path: CString, flags: Int, mode: Int): Int = extern
  def mdb_txn_begin(
      env: Env,
      parent: Ptr[Byte],
      flags: Int,
      tx: Ptr[Transaction]
  ): Int = extern
  def mdb_dbi_open(
      tx: Transaction,
      name: CString,
      flags: Int,
      db: Ptr[DB]
  ): Int = extern
  def mdb_put(
      tx: Transaction,
      db: DB,
      key: Ptr[Key],
      value: Ptr[Value],
      flags: Int
  ): Int = extern
  def mdb_txn_commit(tx: Transaction): Int = extern
  def mdb_get(tx: Transaction, db: DB, key: Ptr[Key], value: Ptr[Value]): Int =
    extern
  def mdb_txn_abort(tx: Transaction): Int = extern
