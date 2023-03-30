package `09lmdbSimple`

import scalanative.unsigned.UnsignedRichInt
import scalanative.unsafe.*
import scalanative.libc.*
import scalanative.unsigned.*
import argonaut.*
import Argonaut.*
import LMDB.*

val lineBuffer = stdlib.malloc(1024.toULong)
val getKeyBuffer = stdlib.malloc(512.toULong)
val putKeyBuffer = stdlib.malloc(512.toULong)
val valueBuffer = stdlib.malloc(512.toULong)

// @main
def lmdbSimple(args: String*): Unit =
  val env = LMDB.open(c"./db")
  stdio.printf(c"opened db %p\n", env)
  stdio.printf(c"> ")

  while stdio.fgets(lineBuffer, 1024, stdio.stdin) != null do
    val put_scan_result =
      stdio.sscanf(lineBuffer, c"put %s %s", putKeyBuffer, valueBuffer)
    val get_scan_result = stdio.sscanf(lineBuffer, c"get %s", getKeyBuffer)

    if put_scan_result == 2 then
      stdio.printf(
        c"storing value %s into key %s\n",
        putKeyBuffer,
        valueBuffer
      )
      LMDB.put(env, putKeyBuffer, valueBuffer)
      stdio.printf(c"saved key: %s value: %s\n", putKeyBuffer, valueBuffer)
    else if get_scan_result == 1 then
      stdio.printf(c"looking up key %s\n", getKeyBuffer)
      val lookup = LMDB.get(env, getKeyBuffer)
      stdio.printf(c"retrieved key: %s value: %s\n", getKeyBuffer, lookup)
    else println("didn't understand input")

    stdio.printf(c"> ")

  println("done")

object LMDB:
  import lmdb_impl.*

  def open(path: CString): Env =
    val envPtr = stackalloc[Env](sizeof[Env])
    check(mdb_env_create(envPtr), "mdb_env_create")
    val env = !envPtr
    // Unix permissions for octal 0644 (read/write)
    check(mdb_env_open(env, path, 0, 420), "mdb_env_open")
    env

  def put(env: Env, key: CString, value: CString): Unit =
    val databasePtr = stackalloc[DB](sizeof[DB])
    val transactionPtr = stackalloc[Transaction](sizeof[Transaction])

    check(
      mdb_transactionn_begin(env, null, 0, transactionPtr),
      "mdb_transactionn_begin"
    )
    val transaction = !transactionPtr
    check(mdb_dbi_open(transaction, null, 0, databasePtr), "mdb_dbi_open")
    val db = !databasePtr

    val k = stackalloc[Key](sizeof[Key])
    k._1 = string.strlen(key).toLong + 1.toLong
    k._2 = key

    val v = stackalloc[Value](sizeof[Value])
    v._1 = string.strlen(value).toLong + 1.toLong
    v._2 = value

    check(mdb_put(transaction, db, k, v, 0), "mdb_put")
    check(mdb_transactionn_commit(transaction), "mdb_transactionn_commit")

  def get(env: Env, key: CString): CString =
    val databasePtr = stackalloc[DB](sizeof[DB])
    val transactionPtr = stackalloc[Transaction](sizeof[Transaction])

    check(
      mdb_transactionn_begin(env, null, 0, transactionPtr),
      "mdb_transactionn_begin"
    )
    val transaction = !transactionPtr

    check(mdb_dbi_open(transaction, null, 0, databasePtr), "mdb_dbi_open")
    val database = !databasePtr

    val rk = stackalloc[Key](sizeof[Key])
    rk._1 = string.strlen(key).toLong + 1.toLong
    rk._2 = key
    val rv = stackalloc[Value](sizeof[Value])

    check(mdb_get(transaction, database, rk, rv), "mdb_get")

    stdio.printf(c"key: %s value: %s\n", rk._2, rv._2)
    val output = stdlib.malloc(rv._1.toULong)
    string.strncpy(output, rv._2, rv._1.toULong)
    check(mdb_transactionn_abort(transaction), "mdb_transactionn_abort")
    output

  def check(result: Int, label: String): Unit =
    if result != 0 then
      throw new Exception(s"bad LMDB call: $label returned $result")
    else println(s"$label returned $result")

@link("lmdb")
@extern
object lmdb_impl:
  type Env = Ptr[Byte]
  type DB = UInt
  def mdb_env_create(env: Ptr[Env]): Int = extern
  def mdb_env_open(env: Env, path: CString, flags: Int, mode: Int): Int = extern

  type Transaction = Ptr[Byte]
  type Key = CStruct2[Long, Ptr[Byte]]
  type Value = CStruct2[Long, Ptr[Byte]]
  def mdb_transactionn_begin(
      env: Env,
      parent: Ptr[Byte],
      flags: Int,
      transaction: Ptr[Transaction]
  ): Int = extern
  def mdb_dbi_open(
      transaction: Transaction,
      name: CString,
      flags: Int,
      db: Ptr[DB]
  ): Int = extern
  def mdb_put(
      transaction: Transaction,
      db: DB,
      key: Ptr[Key],
      value: Ptr[Value],
      flags: Int
  ): Int = extern
  def mdb_transactionn_commit(transaction: Transaction): Int = extern

  def mdb_get(
      transaction: Transaction,
      db: DB,
      key: Ptr[Key],
      value: Ptr[Value]
  ): Int =
    extern
  def mdb_transactionn_abort(transaction: Transaction): Int = extern
