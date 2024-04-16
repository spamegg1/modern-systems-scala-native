package ch04
package nativeFork

import scalanative.libc.stdlib

@main
def nativeFork(args: String*): Unit =
  if args.size == 0 then // no arguments provided. Just exit.
    println("bye")
    stdlib.exit(1)

  println("about to fork")

  val status = doAndAwait: () => // task = run command with arguments.
    println(s"in child, about to exec command: ${args.toSeq}")
    runCommand(args) // args: e.g. /bin/ls -l . First arg is the command.

  println(s"wait status ${status}") // child ran command with args, returned status code.

// TODO
// def badThrottle(commands: Seq[Seq[String]], maxParallel: Int) = ???

// TODO
// def goodThrottle(commands: Seq[Seq[String]], maxParallel: Int) = ???

// You can compile this with: (at the root directory of the project)
// scala-cli package . --main-class ch04.nativeFork.nativeFork
// ...
// Wrote /home/spam/Projects/modern-systems-scala-native/project, run it with
//   ./project
// You need to provide arguments to it:
// ./project /bin/ls -l
// about to fork
// forked, got pid 43820
// in proc 43820
// in child, about to exec command: List(/bin/ls, -l)
// proc 43820: running command /bin/ls with args List(/bin/ls, -l)
// total 1584
// -rw-rw-r-- 1 spam spam   13085 Apr 13 11:30 README.md
// drwxrwxr-x 2 spam spam    4096 Apr 13 11:30 images
// -rwxrwxr-x 1 spam spam 1590376 Apr 15 16:31 project
// -rw-rw-r-- 1 spam spam     260 Apr 14 19:12 project.scala
// drwxrwxr-x 3 spam spam    4096 Jan 25  2023 src
// wait status 0
