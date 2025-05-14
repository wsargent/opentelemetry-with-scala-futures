package services

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import org.scalatest.TryValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Second, Seconds}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.components.OneServerPerSuiteWithComponents
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.test.Injecting

import scala.concurrent.ExecutionContext
import scala.util.Try

class MyServiceSpec extends PlaySpec with Matchers with GuiceOneServerPerSuite with ScalaFutures with TryValues with Injecting {

  private val tracer = GlobalOpenTelemetry.getTracer("application")

  lazy val myService: MyService = inject[MyService]

  "MyService" must {

    "fail with getCurrentTime without an active span" in {
      Try(myService.getCurrentTime).failure.exception mustBe an[
        IllegalStateException
      ]
    }

    "work with getCurrentTime with an active span" in {
      implicit val span: Span = tracer.spanBuilder("getCurrentTime").startSpan()
      val scope = span.makeCurrent()
      try {
        myService.getCurrentTime
      } finally {
        span.end()
        scope.close()
      }
    }

    "work with currentTime because it creates its own span" in {
      
      myService.currentTimeWithSpan > 0 mustBe true
    }

    "fail with futureCurrentTime if a span is not active" in {
      
      implicit val span: Span = Span.current
      myService.futureCurrentTime.failed.futureValue mustBe an[
        IllegalStateException
      ]
    }

    "work with futureCurrentTime with an active span" in {
      
      implicit val span: Span = tracer.spanBuilder("futureCurrentTime").startSpan()
      val scope = span.makeCurrent()
      try {
        val f = myService.futureCurrentTime
        f.onComplete(_ => span.end())(using ExecutionContext.global)
        whenReady(f) {
          _ > 0 mustBe true
        }
      } finally {
        scope.close()
      }
    }

    "work getting a cat" in {
      
      implicit val patienceConfig: PatienceConfig = PatienceConfig(
        org.scalatest.time.Span(15, Seconds),
        org.scalatest.time.Span(1, Second)
      )
      whenReady(myService.getCat) { byteString =>
        byteString must not be (empty)
      }
    }

  }

}
