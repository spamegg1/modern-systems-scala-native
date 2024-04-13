package ch09

import scalanative.unsafe.*
import scalanative.unsigned.UInt

@link("lmdb")
@extern
object LmdbImpl:
  type Env = Ptr[Byte]
  type DB = UInt
  type Transaction = Ptr[Byte]
  type Key = CStruct2[Long, Ptr[Byte]]
  type Value = CStruct2[Long, Ptr[Byte]]

  def mdb_env_create(env: Ptr[Env]): Int = extern
  def mdb_env_open(env: Env, path: CString, flags: Int, mode: Int): Int = extern
  def mdb_txn_begin(env: Env, parent: Ptr[Byte], flags: Int, tx: Ptr[Transaction]): Int =
    extern
  def mdb_dbi_open(tx: Transaction, name: CString, flags: Int, db: Ptr[DB]): Int = extern
  def mdb_put(
      tx: Transaction,
      db: DB,
      key: Ptr[Key],
      value: Ptr[Value],
      flags: Int
  ): Int = extern
  def mdb_txn_commit(tx: Transaction): Int = extern
  def mdb_get(tx: Transaction, db: DB, key: Ptr[Key], value: Ptr[Value]): Int = extern
  def mdb_txn_abort(tx: Transaction): Int = extern
