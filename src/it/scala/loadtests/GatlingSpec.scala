package loadtests

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

// load test to see how tracing works under load.
class GatlingSpec extends Simulation {
  private val httpConf = http.baseUrl("http://localhost:9000")

  private val scn = scenario("Clients").exec(GetFuture.refreshManyTimes)

  setUp(
    scn.inject(
      nothingFor(4),
      atOnceUsers(10),
      rampUsers(10).during(5),
      constantUsersPerSec(20).during(15),
      constantUsersPerSec(20).during(15).randomized,
      rampUsersPerSec(10).to(20).during(10.minutes),
      rampUsersPerSec(10).to(20).during(10.minutes).randomized,
      stressPeakUsers(1000).during(20)
    ).protocols(httpConf)
  )
}

object GetFuture {

  def refreshAfterOneSecond =
    exec(http("Future Call").get("/future").check(status.is(200))).pause(1)

  val refreshManyTimes = repeat(500) {
    refreshAfterOneSecond
  }

}
