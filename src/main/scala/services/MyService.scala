package services

import io.opentelemetry.api.common.{AttributeKey, Attributes}
import io.opentelemetry.api.trace.{PropagatedSpan, Span, SpanContext, StatusCode, Tracer}
import org.apache.pekko.util.ByteString
import play.api.libs.ws.WSClient
import services.MyService.resultKey

import javax.inject.*
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class MyService @Inject() (ws: WSClient, tracer: Tracer)(implicit ec: ExecutionContext) extends Logging {

  // ----------------------------------------------------------
  // The simplest case: synchronous methods.

  def getCurrentTime: Long = {
    val span = Span.current()
    if (span.getSpanContext == SpanContext.getInvalid) {
      throw new IllegalStateException("getCurrentTime: no active span!")
    }

    // On shutdown, threads will still be active but will get non-writable spans :-(
    val result = System.currentTimeMillis()
    if (span.isRecording) {
      val attributes = Attributes.of[java.lang.Long](resultKey, result)
      span.addEvent("getCurrentTime", attributes)
    } else {
      logger.warn("getCurrentTime: span is read only! {}", span)
    }
    logger.debug("getCurrentTime: {}", "result" -> result)
    result
  }

  def currentTimeWithSpan: Long = {
    implicit val span: Span = tracer.spanBuilder("currentTime").startSpan()
    val scope = span.makeCurrent()
    try {
      try {
        getCurrentTime
      } catch {
        case e: Exception =>
          span.recordException(e)
          span.setStatus(StatusCode.ERROR)
          throw e
      } finally {
        span.end()
      }
    } finally {
      scope.close()
    }
  }

  // ----------------------------------------------------------
  // Expecting an active span in a Future.

  def futureCurrentTime: Future[Long] = {
    Future {
      getCurrentTime
    }
  }

  def getCat: Future[ByteString] = {
    implicit val span: Span = tracer.spanBuilder("getCat").startSpan()
    val scope = span.makeCurrent()
    try {
      val f = ws.url("https://http.cat/404.jpg")
        .withRequestTimeout(1.seconds)
        .get()
        .map { result =>
          result.bodyAsBytes
        }
      f.onComplete {
        case Success(_) =>
          span.end()
        case Failure(e) =>
          span.recordException(e)
          span.setStatus(StatusCode.ERROR)
          span.end()
      }(using ExecutionContext.parasitic)
      f
    } finally {
      scope.close()
    }
  }

  def futureCurrentTimeWithSpan: Future[Long] = {
    implicit val span: Span =
      tracer.spanBuilder("explicitFutureCurrentTimeWithSpan").startSpan()
    val scope = span.makeCurrent()
    try {
      val f = futureCurrentTime
      f.onComplete {
        case Success(_) =>
          span.end()
        case Failure(e) =>
          span.recordException(e)
          span.setStatus(StatusCode.ERROR)
          span.end()
      }(using ExecutionContext.parasitic)
      f
    } finally {
      scope.close()
    }
  }
}

object MyService {
  val resultKey: AttributeKey[java.lang.Long] = AttributeKey.longKey("result")
}
