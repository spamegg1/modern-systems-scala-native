# Scala Native, using Scala 3

Updating the code in [Modern Systems Programming with Scala Native](https://pragprog.com/titles/rwscala/modern-systems-programming-with-scala-native/) to

- [Scala](https://www.scala-lang.org/) 3.4.1+,
- [Scala Native](https://scala-native.org/en/stable/) version 0.5.0+,
- `scala-cli` version 1.2.1+, and
- not using the Docker container provided by the [book's website](https://media.pragprog.com/titles/rwscala/code/rwscala-code.zip).

So I...

- changed the syntax to Scala 3 syntax:
- removed all the optional braces, added the fewer braces syntax,
- replaced `if (...) {...} else {...}` with `if ... then ... else ...` everywhere, using Python-style indentation,
- removed the `return` keyword, replaced `NonLocalReturns` usage with the new `util.boundary` and `boundary.break`,
- changed all the `snake_case` names to `camelCase`,
- got rid of unnecessary `main` object wrappings and used `@main` annotations instead,
- and so on.

## Compiling and running

You can compile and run `@main` methods in VS Code with Metals by clicking the run button above them:

![run](images/run.png)

### Compiling a `@main` method to a binary executable

There are 35+ `@main` methods in the project. To compile a specific one to a binary, you can use inside the root directory, for example:

```bash
$ scala-cli package . --main-class ch08.simplePipe.simplePipe
```

This will place the binary executable in the project root directory:

```bash
Wrote /home/spam/Projects/modern-systems-scala-native/ch08.simplePipe.simplePipe, run it with
  ./ch08.simplePipe.simplePipe
```

Here the class is the import path to the method: `ch08` and `simplePipe` are package names, and `simplePipe` is the name of the `@main` method:

```scala
package ch08
package simplePipe

// ...

@main
def simplePipe: Unit = ??? // so this is ch08.simplePipe.simplePipe
```

If in doubt, you can use the `--interactive` mode, which lets you pick the `@main` method you want:

```bash
$ scala-cli package . --interactive
Found several main classes. Which would you like to run?
[0] ch01.hello.helloWorld
[1] ch06.asyncTimer.asyncTimer
[2] ch09.jsonSimple.jsonSimple
[3] ch05.httpServer.httpServer05
[4] ch08.fileOutputPipe
[5] ch01.helloNative.helloNative
[6] ch06.asyncHttp.asyncHttp
[7] ch09.lmdbSimple.lmdbSimple
[8] ch08.fileInputPipe
[9] ch01.testing.testNullTermination
[10] ch01.cStringExpr1.cStringExperiment1
[11] ch01.sscanfInt.sscanfIntExample
[12] ch01.badStuff.testingBadStuff
[13] ch08.filePipeOut.filePipeOut
[14] ch02.agg.aggregateAndCount
[15] ch01.goodSscanf.goodSscanfStringParse
[16] ch01.badSscanf.badSscanfStringParse
[17] ch04.badExec.badExec
[18] ch07.simpleAsync.simpleAsync
[19] ch06.asyncTcp.asyncTcp
[20] ch03.http.httpClient
[21] ch08.simplePipe.simplePipe
[22] ch01.maxNgramFast.maxNgramFast
[23] ch07.curlAsync.curlAsync
[24] ch02.sort.sortByCount
[25] ch08.filePipe.filePipeMain
[26] ch03.tcp.tcpClient
[27] ch01.maxNgramNaive.maxNgramNaive
[28] ch04.nativePipeTwo.nativePipeTwo
[29] ch01.moreTesting.run
[30] ch01.cStringExpr2.cStringExperiment2
[31] ch04.nativeFork.nativeFork
[32] ch04.nativePipe.nativePipe
[33] ch10.libUvService.libuvService
[34] bug.run
[35] ch07.timerAsync.timerAsync
21
[info] Linking (multithreadingEnabled=true, disable if not used) (2353 ms)
[info] Discovered 1119 classes and 7040 methods after classloading
[info] Checking intermediate code (quick) (76 ms)
[info] Discovered 1050 classes and 5504 methods after optimization
[info] Optimizing (debug mode) (2199 ms)
[info] Produced 9 LLVM IR files
[info] Generating intermediate code (1689 ms)
[info] Compiling to native code (3083 ms)
[info] Linking with [pthread, dl, uv]
[info] Linking native code (immix gc, none lto) (239 ms)
[info] Postprocessing (0 ms)
[info] Total (9395 ms)
Wrote /home/spam/Projects/modern-systems-scala-native/ch08.simplePipe.simplePipe, run it with
  ./ch08.simplePipe.simplePipe
```

## Differences from the book

I noticed many things have changed.

### Unused lines of code in the book (probably errors)

There are lines of code in the zip file provided on [the book's website](https://media.pragprog.com/titles/rwscala/code/rwscala-code.zip). Some of these are also printed in the book!

For example, in Chapter 4's `nativeFork` there is

```scala
for (j <- (0 to count)) {
}
```

which does nothing. There is also

```scala
val pid = unistd.getpid()
```

which is never used. There are lots of other examples. There are also many unused / unnecessary imports in the files. Whenever I ran into these, I removed them.

There is also a lot of code duplication, I suppose, to make each individual file "runnable" by itself. I removed redundant code by adding package declarations, then importing the duplicated code from other files instead.

For example, Chapter 4's `badExec.scala` duplicates a lot of code from `nativeFork.scala`. I solved it by separating duplicate code into a file, and adding package declarations:

```scala
// this is common.scala
package ch04

// ...
```

```scala
// this is nativeFork.scala
package ch04
package nativeFork

// ...
// then use code from common.scala here
```

```scala
// this is badExec.scala
package ch04
package badExec

// ...
// then use code from common.scala here
```

There is a lot of this duplication in later chapters. I'll fix them.

### `CSize / USize` instead of `Int`

The book uses `Int`s for a lot of calculations such as string length, how much memory should be allocated, etc. But the current version of Scala Native is using `CSize` for these now. So the `Int`s have to be converted. `CSize / USize` are actually `ULong`, so we need `.toCSize`, or `.toUSize`, or `.toULong` conversion. For this, we need to import:

```scala
import scalanative.unsigned.UnsignedRichLong
```

This also works:

```scala
import scalanative.unsigned.UnsignedRichInt
```

Moreover, we are now [able to use direct comparison](https://github.com/scala-native/scala-native/pull/3584) between `CSize` / `USize` types and `Int`. For example:

```scala
// here strlen returns CSize, normally we would have to do 5.toULong
if string.strlen(myCString) != 5 then ???
```

### `stackalloc` default argument with optional parentheses

There are many function calls in the book that only take type arguments and no value arguments, such as `stackalloc[Int]` etc. This is because there is a default argument `n` with value `1` if none is provided, and in Scala 2 we can drop empty parentheses: `stackalloc[Int]` instead of `stackalloc[Int]()`.

In Scala 3, we need to provide the empty parentheses for the default parameter of `1`, or just provide `1` as an argument:

```scala
stackalloc[Int] // does not work in Scala 3
stackalloc[Int]() // this defaults to n = 1
stackalloc[Int](1) // same as previous
```

### Creating function pointers

Function pointer classes now have different syntax. The book overrides classes like `CFuncPtr2` by providing a custom `apply` method like so:

```scala
val by_count = new CFuncPtr2[Ptr[Byte],Ptr[Byte],Int] {
  def apply(p1:Ptr[Byte], p2:Ptr[Byte]):Int = {
    val ngram_ptr_1 = p1.asInstanceOf[Ptr[NGramData]]
    val ngram_ptr_2 = p2.asInstanceOf[Ptr[NGramData]]
    val count_1 = ngram_ptr_1._2
    val count_2 = ngram_ptr_2._2
    return count_2 - count_1
  }
}
```

We can no longer do this, as these classes are declared `final`. We must use the companion object's `fromScalaFunction[...]` method instead (which is nicer, since we don't have to remember that we have to implement `def apply`):

```scala
val byCount = CFuncPtr2.fromScalaFunction[Ptr[Byte], Ptr[Byte], Int]:
  (p1: Ptr[Byte], p2: Ptr[Byte]) =>
    val ngramPtr1 = p1.asInstanceOf[Ptr[NGramData]]
    val ngramPtr2 = p2.asInstanceOf[Ptr[NGramData]]
    ngramPtr2._2 - ngramPtr1._2
```

### String copying and null-terminating

The book uses the usual C idiom of *allocating memory that is 1 more than the length of a string, copying it, then manually null-terminating the new copy*:

```scala
val string_ptr = toCString(arg) // prepare pointer for malloc
val string_len = string.strlen(string_ptr) // calculate length of string to be copied
val dest_str = stdlib.malloc(string_len + 1).asInstanceOf[Ptr[Byte]] // alloc 1 more
string.strncpy(dest_str, string_ptr, arg.size + 1) // copy
dest_str(string_len) = 0 // manually null-terminate the new copy
```

If you do this you'll get errors: first is the `CSize` errors:

```scala
arg.size + 1
```

when you are trying to add 1, which is `Int`, to `string_len`, which is `CSize`, for which you have to use `.toUSize`.

The second is `none of the overloaded alternatives for method update of Ptr[Byte]...` which complains when we are trying to manually null-terminate the new copy of the string:

```scala
dest_str(string_len) = 0
```

It has to be `Byte` instead.

Fixing all these problems and rewriting in Scala 3 style, we get:

```scala
val stringPtr = toCString(arg) // prepare pointer for malloc
val strLen = string.strlen(stringPtr) // calculate length of string to be copied
val destStr = stdlib.malloc(strLen + 1.toUSize) // alloc 1 more
string.strncpy(destStr, stringPtr, strLen) // copy JUST the string, not \0
destStr(strLen) = 0.toByte // manually null-terminate the new copy
```

or we can simply copy the string, including the null-terminator:

```scala
val stringPtr = toCString(arg) // prepare pointer for malloc
val strLen = string.strlen(stringPtr) // calculate length of string to be copied
val destStr = stdlib.malloc(strLen + 1.toUSize) // alloc 1 more
string.strncpy(destStr, stringPtr, strLen + 1.toUSize) // copy, including \0
```

If we for some reason don't trust `strncpy` and want extra super-duper safety, we can do both:

```scala
val stringPtr = toCString(arg) // prepare pointer for malloc
val strLen = string.strlen(stringPtr) // calculate length of string to be copied
val destStr = stdlib.malloc(strLen + 1.toUSize) // alloc 1 more
string.strncpy(destStr, stringPtr, strLen + 1) // copy, including \0
destStr(strLen) = 0.toByte // null-terminate the new copy, JUST IN CASE!
```

Now it's null terminated twice: once with the copying, then again manually.

### Command line arguments: `String*` instead of `Array[String]`

The book uses the old-school C-style "`argv`" approach to command-line arguments from Scala 2:

```scala
object Main {
  def main(args: Array[String]): Unit = {
    ???
  }
}
```

This does not work with Scala 3 `@main` annotations, as it will complain about `no given instance of type scala.util.CommandLineParser.fromString[Array[String]]...` Things have changed in Scala 3 when it comes to main methods, command line arguments and code-running. They have been greatly simplified, the main method no longer has to be named "main", and now there is greater capability to use any user-defined type for the command-line arguments, but the compiler has to be "taught" how to do it.

We could do that by providing the given instance... but instead we fall back on the "arbitrary number of parameters of the same type" approach (and rename the method while we're at it):

```scala
@main
def nativePipeTwo(args: String*): Unit = ???
```

### Unable to reliably reproduce segmentation faults

In Scala 3.4.1+, Native 0.5.0+, the `bad_sscanf_string_parse` example given in the book does not cause a segfault like it does in the book. Or rather, we have to use a *very long* string to get a segfault, like > 100 characters. If we use the author's version (Scala 2.11, Native 0.4.0, and some old SBT version) then it works; we get a segfault immediately with as few as 8 characters every time. It won't segfault even with `stackalloc[CString](1)`.

So I'm gonna drop down into C to see some reliable, reproducible segfault examples.

Well... that produced the same result, only for large string inputs (around 30 characters but not reliably).

```bash
./segfault
dddddddddddddddddddddddddd
scan results: dddddddddddddddddddddddd
ddddddddddddddddddddddddddd
scan results: ddddddddddddddddddddddddddd
dddddddddddddddddddddddddddd
malloc(): corrupted top size
Aborted (core dumped)
```
