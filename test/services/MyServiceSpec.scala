package services

import components.AppComponents
import io.opentelemetry.api.GlobalOpenTelemetry
import org.scalatest.TryValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Second, Seconds, Span}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.components.OneServerPerSuiteWithComponents
import play.api.test.Injecting

import scala.concurrent.ExecutionContext
import scala.util.Try

class MyServiceSpec
    extends PlaySpec
    with Matchers
    with OneServerPerSuiteWithComponents
    with ScalaFutures
    with TryValues {

  override def components: AppComponents = new AppComponents(context)

  private val tracer = GlobalOpenTelemetry.getTracer("application")

  "MyService" must {

    "fail with getCurrentTime without an active span" in {
      val myService = components.service
      Try(myService.getCurrentTime).failure.exception mustBe an[
        IllegalStateException
      ]
    }

    "work with getCurrentTime with an active span" in {
      val myService = components.service
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
      val myService = components.service
      myService.currentTimeWithSpan > 0 mustBe true
    }

    "fail with futureCurrentTime if a span is not active" in {
      val myService = components.service
      myService.futureCurrentTime.failed.futureValue mustBe an[
        IllegalStateException
      ]
    }

    "work with futureCurrentTime with an active span" in {
      val myService = components.service
      val span = tracer.spanBuilder("futureCurrentTime").startSpan()
      val scope = span.makeCurrent()
      try {
        val f = myService.futureCurrentTime
        f.onComplete(_ => span.end())(ExecutionContext.global)
        whenReady(f) {
          _ > 0 mustBe true
        }
      } finally {
        scope.close()
      }
    }

    "fail with brokenDelayedCurrentTime because it activates outside the future" in {
      val myService = components.service
      myService.brokenDelayedCurrentTime.failed.futureValue mustBe an[
        IllegalStateException
      ]
    }

    "work with fixedDelayedCurrentTime because it activates span in the right scope" in {
      val myService = components.service
      whenReady(myService.fixedDelayedCurrentTime) {
        _ > 0 mustBe true
      }
    }

    "work with a Futures that is context aware" in {
      val myService = components.service
      whenReady(myService.contextAwareDelayedCurrentTime) {
        _ > 0 mustBe true
      }
    }

    "work getting a cat" in {
      val myService = components.service
      implicit val patienceConfig: PatienceConfig =
        PatienceConfig(Span(15, Seconds), Span(1, Second))
      whenReady(myService.getCat) { byteString =>
        byteString must not be (empty)
      }
    }

  }

}
