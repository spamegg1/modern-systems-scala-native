Updating the code in [Modern Systems Programming with Scala Native](https://pragprog.com/titles/rwscala/modern-systems-programming-with-scala-native/) to
- [Scala](https://www.scala-lang.org/) 3.2.2,
- [Scala Native](https://scala-native.org/en/stable/) version 0.4.11,
- `sbt` version 1.8.2, and
- not using the Docker container provided by the [book's website](https://media.pragprog.com/titles/rwscala/code/rwscala-code.zip).

So I...

- changed the syntax to Scala 3 syntax:
- removed all the optional braces,
- replaced `if (...) {...} else {...}` with `if ... then ... else ...` everywhere, using Python-style indentation,
- removed the `return` keyword,
- changed all the `snake_case` names to `camelCase`,
- got rid of unnecessary `main` object wrappings and used `@main` annotations instead,
- and so on.

### Differences from the book

I noticed many things have changed.

#### `CSize` instead of `Int`

The book uses `Int`s for a lot of calculations such as string length, how much memory should be allocated, etc. But the current version of Scala Native is using `CSize` for these now. So the `Int`s have to be converted. `CSize` is actually `ULong`, so we need `.toULong` conversion. For this, we need to import:

```scala
import scalanative.unsigned.UnsignedRichInt
```

#### Type arguments alone not enough for `stackalloc`

There are many function calls in the book that only take type arguments and no value arguments, such as `stackalloc[Int]` etc. In current Scala Native, we need to provide value parameters:

```scala
stackalloc[Int](sizeof[Int])
stackalloc[Ptr[Byte]](sizeof[Ptr[Byte]])
// etc.
```

#### Creating function pointers

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

We can no longer do this, as these classes are declared `final`. We must use the companion object's `fromScalaFunction[...]` method instead:

```scala
val byCount = CFuncPtr2.fromScalaFunction[Ptr[Byte], Ptr[Byte], Int](
  (p1: Ptr[Byte], p2: Ptr[Byte]) =>
    val ngramPtr1 = p1.asInstanceOf[Ptr[NGramData]]
    val ngramPtr2 = p2.asInstanceOf[Ptr[NGramData]]
    ngramPtr2._2 - ngramPtr1._2
 )
```

#### String copying and null-terminating

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
when you are trying to add 1, which is `Int`, to `string_len`, which is `CSize`, for which you have to use `.toULong`.

The second is `none of the overloaded alternatives for method update of Ptr[Byte]...` which complains when we are trying to manually null-terminate the new copy of the string:
```scala
dest_str(string_len) = 0
```
We can't use an `Int` as an array index / offset to update the value at a pointer / array location. (But we can use an `Int` as an offset / array index to ACCESS a value. Weird!) The overloaded alternatives to the `update` method want `Word` or `UWord` for the index input, but conversion methods from `Int` to `UWord` don't exist. The documentation [says](https://javadoc.io/doc/org.scala-native/nativelib_native0.4_3/latest/scala/scalanative/unsafe.html#UWord-0) that `UWord` is `ULong` on 64-bit systems. So we need to use `ULong` for pointer-array-access-update.

Fixing all these problems and rewriting in Scala 3 style, we get:
```scala
val stringPtr = toCString(arg) // prepare pointer for malloc
val strLen = string.strlen(stringPtr) // calculate length of string to be copied
val destStr = stdlib.malloc(strLen + 1.toULong) // alloc 1 more
string.strncpy(destStr, stringPtr, strLen) // copy JUST the string, not \0
destStr(strLen.toULong) = 0 // manually null-terminate the new copy
```
or we can simply copy the string, including the null-terminator:
```scala
val stringPtr = toCString(arg) // prepare pointer for malloc
val strLen = string.strlen(stringPtr) // calculate length of string to be copied
val destStr = stdlib.malloc(strLen + 1.toULong) // alloc 1 more
string.strncpy(destStr, stringPtr, strLen + 1.toULong) // copy, including \0
```
If we for some reason don't trust `strncpy` and want extra super-duper safety, we can do both:
```scala
val stringPtr = toCString(arg) // prepare pointer for malloc
val strLen = string.strlen(stringPtr) // calculate length of string to be copied
val destStr = stdlib.malloc(strLen + 1.toULong) // alloc 1 more
string.strncpy(destStr, stringPtr, strLen + 1) // copy, including \0
destStr(strLen.toULong) = 0 // manually null-terminate the new copy JUST IN CASE
```
Now it's null terminated twice: once with the copying, then again manually.

#### Command line arguments: `String*` instead of `Array[String]`

The book uses the old-school C-style "`argv`" approach to command-line arguments from Scala 2:

```scala
object Main {
  def main(args: Array[String]): Unit = {
    ...
  }
}
```

This does not work with Scala 3 `@main` annotations, as it will complain about `no given instance of type scala.util.CommandLineParser.fromString[Array[String]]...` Things have changed in Scala 3 when it comes to main methods, command line arguments and code-running. They have been greatly simplified, the main method no longer has to be named "main", and now there is greater capability to use any user-defined type for the command-line arguments, but the compiler has to be "taught" how to do it.

We could do that by providing the given instance... but instead we fall back on the "arbitrary number of parameters of the same type" approach (and rename the method while we're at it):

```scala
@main
def nativePipeTwo(args: String*): Unit =
  ...
```

