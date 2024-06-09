package services

import io.opentelemetry.api.GlobalOpenTelemetry
import org.scalatest.TryValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.test.Injecting

import scala.util.Try

class MyServiceSpec extends PlaySpec with Matchers with GuiceOneServerPerSuite with Injecting with ScalaFutures with TryValues {
  private val tracer = GlobalOpenTelemetry.getTracer("application")

  "MyService" must {

    "fail with getCurrentTime without an active span" in {
      val myService = inject[MyService]
      Try(myService.getCurrentTime).failure.exception mustBe an[IllegalStateException]
    }

    "work with getCurrentTime with an active span" in {
      val myService = inject[MyService]
      val span = tracer.spanBuilder("getCurrentTime").startSpan()
      val scope = span.makeCurrent()
      try {
        myService.getCurrentTime
      } finally {
        span.end()
        scope.close()
      }
    }

    "work with currentTime because it creates its own span" in {
      val myService = inject[MyService]
      myService.currentTimeWithSpan > 0 mustBe true
    }

    "fail with futureCurrentTime if a span is not active" in {
      val myService = inject[MyService]
      myService.futureCurrentTime.failed.futureValue mustBe an[IllegalStateException]
    }

    "work with futureCurrentTime with an active span" in {
      val myService = inject[MyService]
      val span = tracer.spanBuilder("futureCurrentTime").startSpan()
      val scope = span.makeCurrent()
      whenReady(myService.futureCurrentTime) {
        scope.close()
        span.end()
        _ > 0 mustBe true
      }
    }

    "fail with brokenDelayedCurrentTime because it activates outside the future" in {
      val myService = inject[MyService]
      myService.brokenDelayedCurrentTime.failed.futureValue mustBe an[IllegalStateException]
    }

    "work with fixedDelayedCurrentTime because it activates span in the right scope" in {
      val myService = inject[MyService]
      whenReady(myService.fixedDelayedCurrentTime) {
        _ > 0 mustBe true
      }
    }

    "work with a Futures that is context aware" in {
      val myService = inject[MyService]
      whenReady(myService.contextAwareDelayedCurrentTime) {
        _ > 0 mustBe true
      }
    }

  }

}
