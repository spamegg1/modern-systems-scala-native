package ch05

import scalanative.unsigned.UnsignedRichInt // .toCSize
import scalanative.unsafe.CQuote
import scalanative.posix.unistd
import scalanative.libc.stdlib.malloc
import scalanative.libc.string.strlen
import scalanative.libc.stdio.EOF

import scala.util.boundary, boundary.break

// an "echo server", just writes back whatever it reads.
def handleConnection(connSocket: Int, maxSize: Int = 1024): Unit =
  val message = c"Connection accepted!  Enter a message and it will be echoed back\n"
  val promptWrite = unistd.write(connSocket, message, strlen(message))
  val lineBuffer = malloc(maxSize) // this is never freed, program ends before it can be.

  boundary:
    while true do
      val bytesRead = unistd.read(connSocket, lineBuffer, maxSize.toCSize) // 0.5
      println(s"read $bytesRead bytes")

      if bytesRead == EOF then break() //  connection has been closed by the client

      val bytesWritten = unistd.write(connSocket, lineBuffer, bytesRead.toCSize) // 0.5
      println(s"wrote $bytesWritten bytes")
