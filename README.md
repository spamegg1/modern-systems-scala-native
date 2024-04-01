# Scala Native, using Scala 3

Updating the code in [Modern Systems Programming with Scala Native](https://pragprog.com/titles/rwscala/modern-systems-programming-with-scala-native/) to

- [Scala](https://www.scala-lang.org/) 3.4.1+,
- [Scala Native](https://scala-native.org/en/stable/) version 0.5.0+,
- `scala-cli` version 1.2.1+, and
- not using the Docker container provided by the [book's website](https://media.pragprog.com/titles/rwscala/code/rwscala-code.zip).

So I...

- changed the syntax to Scala 3 syntax:
- removed all the optional braces,
- replaced `if (...) {...} else {...}` with `if ... then ... else ...` everywhere, using Python-style indentation,
- removed the `return` keyword,
- changed all the `snake_case` names to `camelCase`,
- got rid of unnecessary `main` object wrappings and used `@main` annotations instead,
- and so on.

## Compiling and running

You can compile and run `@main` methods in VS Code with Metals by clicking the run button above them:

![run](images/run.png)

## Differences from the book

I noticed many things have changed.

### `CSize / USize` instead of `Int`

The book uses `Int`s for a lot of calculations such as string length, how much memory should be allocated, etc. But the current version of Scala Native is using `CSize` for these now. So the `Int`s have to be converted. `CSize / USize` are actually `ULong`, so we need `.toUSize` conversion. For this, we need to import:

```scala
import scalanative.unsigned.UnsignedRichLong
```

This also works:

```scala
import scalanative.unsigned.UnsignedRichInt
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
