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

- The book uses `Int`s for a lot of calculations such as string length, how much memory should be allocated, etc. But the current version of Scala Native is using `CSize` for these now. So the `Int`s have to be converted. `CSize` is actually `ULong`, so we need `.toULong` conversion. For this, we need to import:

```scala
import scalanative.unsigned.UnsignedRichInt
```

- There are many function calls in the book that only take type arguments and no value arguments, such as `stackalloc[Int]` etc. In current Scala Native, we need to provide value parameters:

```scala
stackalloc[Int](sizeof[Int])
stackalloc[Ptr[Byte]](sizeof[Ptr[Byte]])
// etc.
```

- Function pointer classes now have different syntax. The book overrides classes like `CFuncPtr2` by providing a custom `apply` method like so:

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

- The book uses the usual C idiom of *allocating memory that is 1 more than the length of a string, copying it, then manually null-terminating the new copy*:

```scala
val string_ptr = toCString(arg) // prepare pointer for malloc
val string_len = string.strlen(string_ptr) // calculate length of string to be copied
val dest_str = stdlib.malloc(string_len + 1).asInstanceOf[Ptr[Byte]] // alloc 1 more
string.strncpy(dest_str, string_ptr, arg.size + 1) // copy
dest_str(string_len) = 0 // manually null-terminate the new copy
```

If you do this you'll get errors: first is the `CSize` errors, for which you have to use `.toULong`; the second is `none of the overloaded alternatives for method update of Ptr[Byte]...` which complains about the null termination. We can't use an `Int` to update the value at a pointer / array location. It wants `Word` or `UWord` but conversion methods don't work.

I tested it, and using `strncpy` copies the null-termination of the original string too! Here's the test code:

```scala
@main
def testNullTermination: Unit =
  val cString: CString = c"hello"
  val strLen: CSize = strlen(cString) // this is 6! not 5!
  val buffer: Ptr[Byte] = malloc(strLen) // no need to "add 1"

  strncpy(buffer, cString, strLen)

  for offset <- 0L to strLen.toLong
  do
    val chr: CChar = buffer(offset)
    stdio.printf(
      c"the character '%c' is %d bytes long and has binary value %d\n",
      chr,
      sizeof[CChar],
      chr
    )
```

This prints:

```
the character 'h' is 1 bytes long and has binary value 104
the character 'e' is 1 bytes long and has binary value 101
the character 'l' is 1 bytes long and has binary value 108
the character 'l' is 1 bytes long and has binary value 108
the character 'o' is 1 bytes long and has binary value 111
the character '' is 1 bytes long and has binary value 0
```

As you can see, the string had length 6 including the null character at the end, and the new `buffer` also received a copy of the null termination.

So we don't have to do this manually anymore. The above code from the book can become:

```scala
val stringPtr = toCString(arg) // create pointer
val stringLen = string.strlen(stringPtr) // calculate length
val destString = stdlib.malloc(stringLen).asInstanceOf[Ptr[Byte]] // allocate
string.strncpy(destString, stringPtr, arg.size.toULong) // copy
```

