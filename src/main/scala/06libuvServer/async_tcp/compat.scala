/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
package scala.scalanative
import scalanative.unsafe._
import scalanative.libc.stdio

package object libc {
  implicit class StdioHelpers(val _stdio: libc.stdio.type) extends AnyVal {
    def printf(format: CString, args: CVarArg*): CInt =
      Zone { implicit z =>
        stdio.vprintf(format, toCVarArgList(args.toSeq))
      }

    def sprintf(s: CString, format: CString, args: CVarArg*): CInt =
      Zone { implicit z =>
        stdio.vsprintf(s, format, toCVarArgList(args.toSeq))
      }

    def snprintf(s: CString, n: CSize, format: CString, args: CVarArg*): CInt =
      Zone { implicit z =>
        stdio.vsnprintf(s, n.toInt, format, toCVarArgList(args.toSeq))
      }

    def fprintf(f: Ptr[stdio.FILE], format: CString, args: CVarArg*): CInt =
      Zone { implicit z =>
        stdio.vfprintf(f, format, toCVarArgList(args.toSeq))
      }

    def scanf(format: CString, args: CVarArg*): CInt =
      Zone { implicit z =>
        stdio.vscanf(format, toCVarArgList(args.toSeq))
      }

    def sscanf(s: CString, format: CString, args: CVarArg*): CInt =
      Zone { implicit z =>
        stdio.vsscanf(s, format, toCVarArgList(args.toSeq))
      }

    def fscanf(f: Ptr[stdio.FILE], format: CString, args: CVarArg*): CInt =
      Zone { implicit z =>
        stdio.vfscanf(f, format, toCVarArgList(args.toSeq))
      }

  }
}
