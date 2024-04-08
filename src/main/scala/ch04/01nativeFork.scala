package ch04
package nativeFork

import scalanative.libc.stdlib

@main
def nativeFork(args: String*): Unit =
  if args.size == 0 then
    println("bye")
    stdlib.exit(1)

  println("about to fork")

  val status = doAndAwait: () =>
    println(s"in child, about to exec command: ${args.toSeq}")
    runCommand(args)

  println(s"wait status ${status}")

// TODO
// def badThrottle(commands: Seq[Seq[String]], maxParallel: Int) = ???

// TODO
// def goodThrottle(commands: Seq[Seq[String]], maxParallel: Int) = ???
