#!/usr/bin/env -S scala-cli
//> using dep com.lihaoyi::os-lib:0.10.2

// set environment variables
val gatlingEnv = Map(
  "GATLING_URL" -> "http://localhost:8080",
  "GATLING_USERS" -> "1000",
  "GATLING_REQUESTS" -> "50000",
  "GATLING_RAMP_TIME" -> "0"
)
println("Finished setting up environment variables for Gatling simulation.")
println()

// interactively compile and run the simulation
println("Now running the Gatling binary:")
println()

val sub = os
  .proc("sh", os.pwd / "gatling" / "bin" / "gatling.sh")
  .spawn(
    env = gatlingEnv,
    stdout = os.ProcessOutput.Readlines(println) // show script's output.
  )

// Simulate the interactivity of the script here:
sub.waitFor(2000L) // wait for Gatling script to start and give us a prompt
println(">>>>> Choosing option [1] to run locally!") // show input
sub.stdin.writeLine("1") // run simulation locally
sub.stdin.flush() // send input
sub.waitFor(8000L) // wait for Gatling to check for updates
println(">>>>> Providing optional name: testSim") // show input
sub.stdin.writeLine("testSim") // give optional simulation name
sub.stdin.flush() // send input
sub.waitFor() // wait for simulation to fully run and finish.
