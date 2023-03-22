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
import scala.scalanative.unsigned._

import argonaut._, Argonaut._

object main {
  import LMDB._

  val line_buffer = stdlib.malloc(1024)
  val get_key_buffer = stdlib.malloc(512)
  val put_key_buffer = stdlib.malloc(512)
  val value_buffer = stdlib.malloc(512)

  def main(args:Array[String]):Unit = {
    val env = LMDB.open(c"./db")
    stdio.printf(c"opened db %p\n", env)
    stdio.printf(c"> ")

    while (stdio.fgets(line_buffer, 1024, stdio.stdin) != null) {
      val put_scan_result = stdio.sscanf(line_buffer,c"put %s %s",
        put_key_buffer, value_buffer)
      val get_scan_result = stdio.sscanf(line_buffer,c"get %s",
        get_key_buffer)

      if (put_scan_result == 2) {
        stdio.printf(c"storing value %s into key %s\n",
            put_key_buffer, value_buffer)
        LMDB.put(env,put_key_buffer,value_buffer)
        stdio.printf(c"saved key: %s value: %s\n", put_key_buffer, value_buffer)
      } else if (get_scan_result == 1) {
        stdio.printf(c"looking up key %s\n", get_key_buffer)
        val lookup = LMDB.get(env,get_key_buffer)
        stdio.printf(c"retrieved key: %s value: %s\n", get_key_buffer,lookup)
      } else {
        println("didn't understand input")
      }
      stdio.printf(c"> ")
    }
    println("done")
  }
}

object LMDB {
  import lmdb_impl._

  def open(path:CString):Env = {
    val env_ptr = stackalloc[Env]
    check(mdb_env_create(env_ptr), "mdb_env_create")
    val env = !env_ptr
    // Unix permissions for octal 0644 (read/write)
    check(mdb_env_open(env, path, 0, 420), "mdb_env_open")
    env
  }

  def put(env:Env,key:CString,value:CString):Unit = {
    val db_ptr = stackalloc[DB]
    val tx_ptr = stackalloc[Transaction]

    check(mdb_txn_begin(env, null, 0, tx_ptr), "mdb_txn_begin")
    val tx = !tx_ptr
    check(mdb_dbi_open(tx,null,0,db_ptr), "mdb_dbi_open")
    val db = !db_ptr

    val k = stackalloc[Key]
    k._1 = string.strlen(key) + 1
    k._2 = key
    val v = stackalloc[Value]
    v._1 = string.strlen(value) + 1
    v._2 = value

    check(mdb_put(tx, db, k,v,0), "mdb_put")
    check(mdb_txn_commit(tx), "mdb_txn_commit")
  }

  def get(env:Env,key:CString):CString = {
    val db_ptr = stackalloc[DB]
    val tx_ptr = stackalloc[Transaction]

    check(mdb_txn_begin(env, null, 0, tx_ptr), "mdb_txn_begin")
    val tx = !tx_ptr

    check(mdb_dbi_open(tx,null,0,db_ptr), "mdb_dbi_open")
    val db = !db_ptr

    val rk = stackalloc[Key]
    rk._1 = string.strlen(key) + 1
    rk._2 = key
    val rv = stackalloc[Value]

    check(mdb_get(tx,db, rk, rv), "mdb_get")

    stdio.printf(c"key: %s value: %s\n", rk._2, rv._2)
    val output = stdlib.malloc(rv._1)
    string.strncpy(output,rv._2,rv._1)
    check(mdb_txn_abort(tx), "mdb_txn_abort")
    return output
  }

  def check(result:Int, label:String):Unit = {
    if (result != 0) {
      throw new Exception(s"bad LMDB call: $label returned $result")
    } else {
      println(s"$label returned $result")
    }
  }
}

@link("lmdb")
@extern
object lmdb_impl {
  type Env = Ptr[Byte]
  type DB = UInt
  def mdb_env_create(env:Ptr[Env]):Int = extern
  def mdb_env_open(env:Env, path:CString, flags:Int, mode:Int):Int = extern

  type Transaction = Ptr[Byte]
  type Key = CStruct2[Long,Ptr[Byte]]
  type Value = CStruct2[Long,Ptr[Byte]]
  def mdb_txn_begin(env:Env, parent:Ptr[Byte], flags:Int,
    tx:Ptr[Transaction]):Int = extern
  def mdb_dbi_open(tx:Transaction, name:CString, flags:Int,
    db:Ptr[DB]):Int = extern
  def mdb_put(tx:Transaction, db:DB, key:Ptr[Key], value:Ptr[Value],
    flags:Int):Int = extern
  def mdb_txn_commit(tx:Transaction):Int = extern

  def mdb_get(tx:Transaction, db:DB, key:Ptr[Key],
    value:Ptr[Value]):Int = extern
  def mdb_txn_abort(tx:Transaction):Int = extern
}
