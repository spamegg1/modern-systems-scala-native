/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import scala.scalanative.libc._
import argonaut._, Argonaut._

object LMDB {
  import lmdb_impl._

  def open(path:CString):Env = {
    val env_ptr = stackalloc[Env]
    check(mdb_env_create(env_ptr), "mdb_env_create")
    val env = !env_ptr
    check(mdb_env_open(env, path, 0, 420), "mdb_env_open") // Unix permissions for 0644 (read/write)
    env
  }

  def getJson[T](env:Env, key:String)(implicit dec:DecodeJson[T]):T = {
    val value = getString(env,key)
    value.decodeOption[T].get
  }

  def putJson[T](env:Env, key:String, value:T)
                (implicit enc:EncodeJson[T]):Unit = {
    val valueString = value.asJson.nospaces
    putString(env,key,valueString)
  }

  def getString(env:Env, key:String):String = {
    Zone { implicit z =>
      val k = toCString(key)
      fromCString(get(env,k))
    }
  }

  def putString(env:Env, key:String, value:String):Unit = {
    Zone { implicit z =>
      val k = toCString(key)
      val v = toCString(value)
      put(env,k,v)
    }
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
  type Transaction = Ptr[Byte]
  type DB = UInt
  type Key = CStruct2[Long,Ptr[Byte]]
  type Value = CStruct2[Long,Ptr[Byte]]
  def mdb_env_create(env:Ptr[Env]):Int = extern
  def mdb_env_open(env:Env, path:CString, flags:Int, mode:Int):Int = extern
  def mdb_txn_begin(env:Env, parent:Ptr[Byte], flags:Int, tx:Ptr[Transaction]):Int = extern
  def mdb_dbi_open(tx:Transaction, name:CString, flags:Int, db:Ptr[DB]):Int = extern
  def mdb_put(tx:Transaction, db:DB, key:Ptr[Key], value:Ptr[Value], flags:Int):Int = extern
  def mdb_txn_commit(tx:Transaction):Int = extern
  def mdb_get(tx:Transaction, db:DB, key:Ptr[Key], value:Ptr[Value]):Int = extern
  def mdb_txn_abort(tx:Transaction):Int = extern
}
