# Modern Systems in Scala Native, using Scala 3 :rocket:

***Update (July 2024)***

The book is getting really hard to understand.
I'm on Chapter 7 and I don't really know what's going on.
Promises, timer handles, `libcurl`...
The book doesn't really explain what we're doing and why.
It's probably written for much more experienced people
who strongly internalized low-level stuff.
Anyway, please keep on reading. Thanks for dropping by!

Updating the code in [Modern Systems Programming with Scala Native](https://pragprog.com/titles/rwscala/modern-systems-programming-with-scala-native/) to

- [Scala](https://www.scala-lang.org/) 3.5.0+,
- [Scala Native](https://scala-native.org/en/stable/) version 0.5.0+,
- `scala-cli` version 1.4.0+, and
- not using the Docker container provided by the [book's website](https://media.pragprog.com/titles/rwscala/code/rwscala-code.zip).

So I...

- changed the syntax to Scala 3 syntax:
- removed all the optional braces, added the fewer braces syntax,
- replaced `if (...) {...} else {...}` with `if ... then ... else ...` everywhere, using Python-style indentation,
- removed the `return` keyword, replaced `NonLocalReturns` usage with the new `util.boundary` and `boundary.break`,
- changed all the `snake_case` names to `camelCase`,
- got rid of unnecessary `main` object wrappings and used `@main` annotations instead,
- changed Bash scripts to Scala-cli scripts,
- and so on.

## Compiling and running

We are using [Scala-cli](https://scala-cli.virtuslab.org/),
so [SBT](https://www.scala-sbt.org/)
(or Mill, or any other build tool) is not needed.

For Scala Native, you'll need the requirements such as Clang / LLVM stuff
as listed on [Scala Native page](https://scala-native.org/en/stable/user/setup.html).

You can compile and run `@main` methods in VS Code with Metals by clicking the run button above them:

![run](images/run.png)

### Compiling a `@main` method to a binary executable

There are 35+ `@main` methods in the project. To compile a specific one to a binary, you can use inside the root directory, for example:

```bash
scala-cli package . --main-class ch08.simplePipe.run
```

This will place the binary executable in the project root directory:

```bash
Wrote /home/spam/Projects/modern-systems-scala-native/ch08.simplePipe.run, run it with
  ./ch08.simplePipe.run
```

Here the class is the import path to the method: `ch08` and `simplePipe` are package names, and `run` is the name of the `@main` method:

```scala
package ch08
package simplePipe

// ...

@main
def run: Unit = ??? // so this is ch08.simplePipe.run
```

If in doubt, you can use the `--interactive` mode, which lets you pick the `@main` method you want:

```bash
$ scala-cli package . --interactive
Found several main classes. Which would you like to run?
[0] ch01.helloWorld.run
[1] ch06.asyncTimer.run
[2] ch09.jsonSimple.run
[3] ch05.httpServer.run
[4] ch08.fileOutputPipe.run
[5] ch01.helloNative.run
[6] ch06.asyncHttp.run
[7] ch09.lmdbSimple.run
[8] ch08.fileInputPipe.run
[9] ch01.testNullTermination.run
[10] ch01.cStringExperiment1.run
[11] ch01.sscanfIntExample.run
[12] ch01.testingBadStuff.run
[13] ch08.filePipeOut.run
[14] ch02.aggregateAndCount.run
[15] ch01.goodSscanfStringParse.run
[16] ch01.badSscanfStringParse.run
[17] ch04.badExec.run
[18] ch07.simpleAsync.run
[19] ch06.asyncTcp.run
[20] ch03.httpClient.run
[21] ch08.simplePipe.run
[22] ch01.maxNgramFast.run
[23] ch07.curlAsync.run
[24] ch02.sortByCount.run
[25] ch08.filePipe.run
[26] ch03.tcpClient.run
[27] ch01.maxNgramNaive.run
[28] ch04.nativePipeTwo.run
[29] ch01.moreTesting.run
[30] ch01.cStringExperiment2.run
[31] ch04.nativeFork.run
[32] ch04.nativePipe.run
[33] ch10.libUvService.run
[34] ch01.bug.run
[35] ch07.timerAsync.run
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
Wrote /home/spam/Projects/modern-systems-scala-native/ch08.simplePipe.run, run it with
  ./ch08.simplePipe.run
```

### Linking to external C libraries

The book uses `@link` and `= extern` constructs of Scala Native to link with libraries such as `libuv`, `libcurl` and `liblmdb`. For example:

```scala
@link("lmdb")
@extern
object LmdbImpl:
  def mdb_env_create(env: Ptr[Env]): Int = extern
  def mdb_env_open(env: Env, path: CString, flags: Int, mode: Int): Int = extern
```

On Ubuntu I had to install these (I think `libcurl` might have been pre-installed already?):

```bash
sudo apt install clang libuv1-dev libcurl4-gnutls-dev liblmdb-dev libhttp-parser-dev
```

The author did all of this work. But if we wanted to do this on our own,
it would be difficult to get right the type signatures of the functions.
Scala Native main contributor's advice is to directly take
[the header file of such a library](https://github.com/libuv/libuv/blob/v1.x/include/uv.h),
and use [`sn-bindgen`](https://github.com/indoorvivants/sn-bindgen) to generate the bindings:

![bindgen](images/bindgen.png)

I haven't tried that myself, but that's the way to go.

#### Unmaintained Node HTTP parser library (WIP)

The [`http-parse` library of Chapter 10](https://github.com/nodejs/http-parser) is no longer maintained.
It was ported to [llhttp](https://github.com/nodejs/llhttp).
It is possible to install this on Ubuntu with

```bash
sudo apt install node-llhttp
```

But I don't know how to link it with Scala Native.
It's written in Typescript, which generates C output.
The output then has to be compiled, and then linked to SN.

### Running the Gatling load simulation

I modified the `install_gatling.sh` script from the book, now it's a Scala-cli
script `scripts/installGatling.sc` with Gatling bundle version 3.10.5+.

From the root directory, run

```bash
./scripts/installGatling.sc
```

This will download into a folder `gatling` in the root directory,
and copy the simulation file from chapter 5 into the relevant subdirectory.

You need to compile and run the HTTP server from chapter 5. (Also on chapter 7.)
Read the compilation message for the name of the binary executable:

```bash
scala-cli package . --main-class ch05.httpServer.run
...
Wrote /home/spam/Projects/modern-systems-scala-native/project, run it with
  ./project
```

Then run that to start the server. This starts the server listening on port 8080.

I wrote another script `scripts/runGatling.sc` that sets up the needed environment variables
then handles the interactive simulation for you by providing necessary inputs.
This will compile the simulation file under `gatling/user-files/simulations/`.
These files have to be written in Scala 2.13 unfortunately!
Gatling cannot handle Scala 3. So... run the simulation with:

```bash
./scripts/runGatling.sc
```

Here's what the Terminal output looks like:

```bash
$ ./scripts/runGatling.sc
Finished setting up environment variables for Gatling simulation.

Now running the Gatling binary:

GATLING_HOME is set to /home/spam/Projects/modern-systems-scala-native/gatling
Do you want to run the simulation locally, on Gatling Enterprise, or just package it?
Type the number corresponding to your choice and press enter
[0] <Quit>
[1] Run the Simulation locally
[2] Package and upload the Simulation to Gatling Enterprise Cloud, and run it there
[3] Package the Simulation for Gatling Enterprise
[4] Show help and exit
>>>>> Choosing option [1] to run locally!
Gatling 3.11.1 is available! (you're using 3.10.5)
ch05.loadSimulation.GenericSimulation is the only simulation, executing it.
Select run description (optional)
>>>>> Providing optional name: testSim
Simulation ch05.loadSimulation.GenericSimulation started...

================================================================================
2024-04-28 18:21:04 GMT                                       2s elapsed
---- Requests ------------------------------------------------------------------
> Global                                                   (OK=5000   KO=0     )
> Web Server                                               (OK=5000   KO=0     )

---- Test scenario -------------------------------------------------------------
[##########################################################################]100%
          waiting: 0      / active: 0      / done: 100
================================================================================

Simulation ch05.loadSimulation.GenericSimulation completed in 2 seconds
Parsing log file(s)...
Parsing log file(s) done in 0s.
Generating reports...

================================================================================
---- Global Information --------------------------------------------------------
> request count                                       5000 (OK=5000   KO=0     )
> min response time                                      5 (OK=5      KO=-     )
> max response time                                    116 (OK=116    KO=-     )
> mean response time                                    38 (OK=38     KO=-     )
> std deviation                                         15 (OK=15     KO=-     )
> response time 50th percentile                         35 (OK=35     KO=-     )
> response time 75th percentile                         50 (OK=50     KO=-     )
> response time 95th percentile                         64 (OK=64     KO=-     )
> response time 99th percentile                         72 (OK=72     KO=-     )
> mean requests/sec                                   2500 (OK=2500   KO=-     )
---- Response Time Distribution ------------------------------------------------
> t < 800 ms                                          5000 (100%)
> 800 ms <= t < 1200 ms                                  0 (  0%)
> t >= 1200 ms                                           0 (  0%)
> failed                                                 0 (  0%)
================================================================================

Reports generated, please open the following file: file:///home/spam/Projects/modern-systems-scala-native/gatling/results/genericsimulation-20240428182101491/index.html
```

The graphical results are in `gatling/results/.../index.html`.
With 1000 users and 50000 requests, I got 1% failure rate
(connection timeouts), and 300ms average response time.
Quite amazing!

![gatling-simul](images/simul.png)

If I use the async server using `libuv` and the event loop in chapter 7,
then again with 1000 users and 50000 requests,
I get 100% success with 231ms mean response time! Great!

![gatling-simul2](images/simul2.png)

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

which is never used. There are also illegal things like:

```scala
val p = SyncPipe(0)
val p = FilePipe(c"./data.txt")
```

There are lots of other examples. There are also many unused / unnecessary imports in the files. Whenever I ran into these, I removed them.

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

There is a lot of this duplication in later chapters. I fixed them.

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

### Type puns via `.cast`

The book uses things like

```scala
val server_sockaddr = server_address.cast[Ptr[sockaddr]]
```

`.cast` is no longer available; we use `.asInstanceOf[...]` instead.

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

### Typos, type puns and signatures for `libuv` (and other external C libraries)

The book and the code have some inconsistencies.
There are sometimes two different names for the same thing,
and the types are also different: For example:

```scala
// these are supposed to be the same thing.
type Timer = Ptr[Ptr[Byte]]       // book, ch06
type TimerHandle = Ptr[Byte]      // book, later in the same chapter

type TimerHandle = Ptr[Byte]      // code, in ch06
type TimerHandle = Ptr[Ptr[Byte]] // code, in other chapters
```

The book clearly says, in a "warning box":

![type-puns](images/typePuns.png)

There are many more issues. For example, given:

```scala
type TCPHandle = Ptr[Ptr[Byte]] // book and code
type ClientState = CStruct3[Ptr[Byte], CSize, CSize]
```

but then:

```scala
val closeCB = CFuncPtr1.fromScalaFunction[TCPHandle, Unit]:
  (client: TCPHandle) =>
    // ...
    val clientStatePtr = (!client).asInstanceOf[Ptr[ClientState]]
```

Since `client` is `TCPHandle = Ptr[Ptr[Byte]]`, `!client` is `Ptr[Byte]`.
So we are casting a `Ptr[Byte]` into a `Ptr[CStruct3[Ptr[Byte], CSize, CSize]]`!
Does this imply `Byte = CStruct3[Ptr[Byte], CSize, CSize]`?
No, it does not work that way I think... :confused:

There are many more instances of this. For example, given

```scala
type TCPHandle = Ptr[Ptr[Byte]]
type ShutdownReq = Ptr[Ptr[Byte]]
```

we have:

```scala
def shutdown(client: TCPHandle): Unit =
  val shutdownReq = malloc(uv_req_size(UV_SHUTDOWN_REQ_T)).asInstanceOf[ShutdownReq]
  !shutdownReq = client.asInstanceOf[Ptr[Byte]]
```

Again, here `!shutdownReq` is a `Ptr[Byte]`, but `client` is a `Ptr[Ptr[Byte]]`.
So we are trying to squeeze a `Ptr[Ptr[Byte]]` into a `Ptr[Byte]`!
We do this by pretending that the nested pointer does not exist with `asInstanceOf[]`.
OK fine, we can trick the compiler this way, but can we later actually use the inner
nested pointer of `client` correctly?
Because later these are passed to actual `libuv` functions...

:confused: :confused: :confused:

***Big brain moment:*** basically, pretty much *anything* can be cast to `Ptr[Byte]`...
Since "everything is a byte", the "beginning of a block of anything" is a `Ptr[Byte]`!

:brain: :brain: :brain: :tada: :confetti_ball: :partying_face:

Not sure how to handle this, it will be guesswork.
If compilation fails during linking phase then I'll know the types are wrong.
But if linking does not fail, then I'll have to figure it out from the execution.

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
