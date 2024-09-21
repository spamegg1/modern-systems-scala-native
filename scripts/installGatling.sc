#!/usr/bin/env -S scala-cli
//> using dep com.lihaoyi::os-lib:0.10.7

// download Gatling Highcharts Bundle
println("Downloading Gatling 3.10.5 to directory ./gatling")
println()

val repo = "https://repo1.maven.org/maven2/io/gatling/highcharts/"
val ver = "gatling-charts-highcharts-bundle/3.10.5/"
val file = "gatling-charts-highcharts-bundle-3.10.5-bundle.zip"
val url = repo + ver + file

os.proc("curl", "-O", url).call() // download using curl

// unzip, cleanup and rename folder
os.proc("unzip", "gatling-charts-highcharts-bundle-3.10.5-bundle.zip").call()
os.proc("rm", "gatling-charts-highcharts-bundle-3.10.5-bundle.zip").call()
os.proc("mv", "gatling-charts-highcharts-bundle-3.10.5", "gatling").call()

println()
println("Installed gatling 3.10.5 to directory ./gatling")
println()

// There is a simulation (written in Java) included by default. Remove that.
val simDir = "./gatling/user-files/simulations"
os.proc("rm", "-rf", s"$simDir/computerdatabase").call()

println(s"Removed included-by-default Java simulations in: $simDir")
println()

// Place our simulation there.
os.proc(
  "cp",
  "src/main/scala/ch05/examples/loadSimulation.scala",
  "./gatling/user-files/simulations/"
).call()

println("Copied ch05 load simulation into ./gatling/user-files/simulations/")
println()

println("Now start the Http server on port 8080 in another Terminal,")
println("and then run the simulation with:")
println("./scripts/runGatling.sc")
