package ch07

import scalanative.unsafe.Ptr
import scala.scalanative.runtime.{Boxes, Intrinsics}

object Curl:
  import LibCurl.*, LibCurlConstants.*
  import LibUV.*, LibUVConstants.*

  def funcToPtr(f: Object): Ptr[Byte] = Boxes.boxToPtr[Byte](Boxes.unboxToCFuncPtr1(f))
  def intToPtr(i: Int): Ptr[Byte] = Boxes.boxToPtr[Byte](Intrinsics.castIntToRawPtr(i))
  def longToPtr(l: Long): Ptr[Byte] = Boxes.boxToPtr[Byte](Intrinsics.castLongToRawPtr(l))
