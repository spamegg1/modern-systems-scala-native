package ch05
package loadSimulation

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import concurrent.duration.DurationInt

// This file uses Scala 2.13 syntax because Gatling bundle does not support Scala 3.
class GenericSimulation extends Simulation {
  val url = System.getenv("GATLING_URL")
  val requests = Integer.parseInt(System.getenv("GATLING_REQUESTS"))
  val users = Integer.parseInt(System.getenv("GATLING_USERS"))
  val rampTime = Integer.parseInt(System.getenv("GATLING_RAMP_TIME"))
  val reqsPerUser: Int = requests / users

  val scn = scenario("Test scenario").repeat(reqsPerUser) {
    exec(
      http("Web Server")
        .get(url)
        .check(status.in(Seq(200, 304)))
    )
  }

  setUp(
    scn.inject(
      rampUsers(users)
        .during(rampTime.seconds) // .during replaced .over
    )
  )
}
