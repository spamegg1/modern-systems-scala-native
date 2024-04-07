package ch05.loadSimulation

import io.gatling.core.Predef.*
import io.gatling.http.Predef.*
import concurrent.duration.DurationInt

val GATLING_URL = "http://localhost:8080"
val GATLING_USERS = 10
val GATLING_REQUESTS = 50
val GATLING_RAMP_TIME = 0

class GenericSimulation extends Simulation:
  val url = GATLING_URL // System.getenv("GATLING_URL")
  val requests = GATLING_REQUESTS // Integer.parseInt(System.getenv("GATLING_REQUESTS"))
  val users = GATLING_USERS // Integer.parseInt(System.getenv("GATLING_USERS"))
  val rampTime = GATLING_RAMP_TIME // Integer.parseInt(System.getenv("GATLING_RAMP_TIME"))

  val reqs_per_user: Int = requests / users
  val scn = scenario("Test scenario").repeat(reqs_per_user):
    exec(http("Web Server").get(url).check(status.in(Seq(200, 304))))

  setUp(scn.inject(rampUsers(users).during(rampTime.seconds))) // .during replaced .over
