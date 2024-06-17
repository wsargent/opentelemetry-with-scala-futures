package services

import com.tersesystems.echopraxia.plusscala.LoggerFactory
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.{AttributeKey, Attributes}
import io.opentelemetry.api.trace.{Span, StatusCode}
import io.opentelemetry.context.Context
import logging.Logging
import org.apache.pekko.util.ByteString
import play.api.libs.concurrent.Futures
import play.api.libs.ws.WSClient
import services.MyService.resultKey
import sourcecode.Enclosing

import java.lang
import javax.inject._
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class MyService @Inject() (
    futures: Futures,
    contextAwareFutures: MyFutures,
    ws: WSClient
)(implicit ec: ExecutionContext)
    extends Logging {
  private val tracer = GlobalOpenTelemetry.getTracer("application")

  private val logger = LoggerFactory.getLogger

  private def assertSpan()(implicit
      enclosing: Enclosing,
      line: sourcecode.Line,
      expectedSpan: Span
  ): Span = {
    assertSpan(Option(Span.fromContextOrNull(Context.current)))
  }

  private def assertSpan(
      maybeSpan: Option[Span]
  )(implicit enclosing: Enclosing, line: sourcecode.Line, expectedSpan: Span): Span = {
    if (maybeSpan.isEmpty) {
      logger.error(s"assertSpan: no span found, expected {}", expectedSpan)
      throw new IllegalStateException(s"No span at ${enclosing.value} line ${line.value}!")
    }
    val span = maybeSpan.get
    if (span != expectedSpan) {
      logger.error(s"assertSpan: {} != {}", span, expectedSpan)
      throw new IllegalStateException(s"Unexpected span from ${enclosing.value} line ${line.value}! ${span.getSpanContext.getSpanId} != ${expectedSpan.getSpanContext.getSpanId}")
    }
    logger.debug(s"assertSpan: {}", span)
    span
  }

  // ----------------------------------------------------------
  // The simplest case: synchronous methods.

  def getCurrentTime(implicit
      enc: sourcecode.Enclosing,
      line: sourcecode.Line,
      expectedSpan: Span
  ): Long = {
    val span = assertSpan()

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
    implicit val span = tracer.spanBuilder("currentTime").startSpan()
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

  def futureCurrentTime(implicit expectedSpan: Span): Future[Long] = {
    Future {
      getCurrentTime
    }
  }

  def forceScope[A](parentContext: Context, span: Span)(block: => A): A = {
    val context = parentContext.`with`(span)
    val scope = context.makeCurrent
    try {
      block
    } finally {
      scope.close()
    }
  }

  def getCat: Future[ByteString] = {
    implicit val span = tracer.spanBuilder("getCat").startSpan()
    val scope = span.makeCurrent()
    val parentContext = Context.current()
    try {
      val f = ws
        .url("https://http.cat/404.jpg")
        .withRequestTimeout(1.seconds)
        .get()
        .map { result =>
          assertSpan()
          result.bodyAsBytes
        }
      f.onComplete {
        case Success(_) =>
          span.end()
        case Failure(e) =>
          span.recordException(e)
          span.setStatus(StatusCode.ERROR)
          span.end()
      }(ExecutionContext.parasitic)
      f
    } finally {
      scope.close()
    }
  }

  def futureCurrentTimeWithSpan: Future[Long] = {
    implicit val span =
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
      }(ExecutionContext.parasitic)
      f
    } finally {
      scope.close()
    }
  }

  // ----------------------------------------------------------------------
  // If we make an active span around `delayedCurrentTime`, it won't work.

  def brokenDelayedCurrentTime: Future[Long] = {
    implicit val span = tracer.spanBuilder("brokenDelayedCurrentTime").startSpan()
    val scope = span.makeCurrent()
    try {
      val delayed = futures.delayed(10.millis) {
        futureCurrentTime
      }
      delayed.onComplete {
        case Success(_) =>
          span.end()
        case Failure(e) =>
          span.recordException(e)
          span.setStatus(StatusCode.ERROR)
          span.end()
      }(ExecutionContext.parasitic)
      delayed
    } finally {
      scope.close()
    }
  }

  // ----------------------------------------------------------------------
  // We have to explicitly activate the span inside the delayed block to fix it.

  def fixedDelayedCurrentTime: Future[Long] = {
    implicit val span = tracer.spanBuilder("fixedDelayedCurrentTime").startSpan()
    val delayed = futures.delayed(10.millis) {
      val scope = span.makeCurrent()
      try {
        futureCurrentTime
      } finally {
        scope.close()
      }
    }
    delayed.onComplete {
      case Success(_) =>
        span.end()
      case Failure(e) =>
        span.recordException(e)
        span.setStatus(StatusCode.ERROR)
        span.end()
    }(ExecutionContext.parasitic)
    delayed
  }

  // ----------------------------------------------------------------------
  // A better fix to this is to ensure that all execution contexts can carry the otel context
  // over asynchronous boundaries.

  def contextAwareDelayedCurrentTime: Future[Long] = {
    implicit val span = tracer.spanBuilder("contextAwareDelayedCurrentTime").startSpan()
    val scope = span.makeCurrent()
    try {
      // contextAwareFutures knows about the activated span and will propagate it
      // correctly between threads
      val delayed = contextAwareFutures.delayed(10.millis) {
        futureCurrentTime
      }
      delayed.onComplete {
        case Success(_) =>
          span.end()
        case Failure(e) =>
          span.recordException(e)
          span.setStatus(StatusCode.ERROR)
          span.end()
      }(ExecutionContext.parasitic)
      delayed
    } finally {
      scope.close()
    }
  }

  // Some utility methods for managing spans, commented out because they're not needed for the examples
  def makeCurrent[F](f: => F)(implicit span: Span): F = {
    val scope = span.makeCurrent()
    try {
      f
    } finally {
      scope.close()
    }
  }

  def traceFuture[F](spanName: String)(producesFuture: => Future[F]): Future[F] = {
    implicit val span: Span = tracer.spanBuilder(spanName).startSpan()
    val f = makeCurrent(producesFuture)
    f.onComplete {
      case Success(_) =>
        span.end()
      case Failure(e) =>
        span.recordException(e)
        span.setStatus(StatusCode.ERROR)
        span.end()
    }(ExecutionContext.parasitic)
    f
  }

}

object MyService {
  val resultKey: AttributeKey[java.lang.Long] = AttributeKey.longKey("result")
}
