package ch04.badExec

import ch04.common

@main
def badExec(args: String*): Unit =
  println("about to exec")
  common.runCommand(Seq("/bin/ls", "-l", "."))
  println("exec returned, we're done!")

// It is executed inside the .scala-build folder (does not see hidden .bloop folder).
// It prints:
// about to exec
// total 12
// -rw-rw-r-- 1 spam spam   60 Mar 30 16:04 ide-inputs.json
// -rw-rw-r-- 1 spam spam    2 Mar 30 16:04 ide-options-v2.json
// drwxrwxr-x 4 spam spam 4096 Apr  4 12:02 modern-systems-scala-native_f4dd477a3a
// Notice that it does not print "exec returned, we're done!"
// Because execve DOES NOT RETURN on success!!!
