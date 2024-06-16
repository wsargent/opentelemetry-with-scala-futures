package services

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.{Span, StatusCode}
import io.opentelemetry.context.Context
import org.apache.pekko.util.ByteString
import play.api.libs.concurrent.Futures
import play.api.libs.ws.WSClient
import sourcecode.Enclosing

import javax.inject.*
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class MyService @Inject() (
    futures: Futures,
    contextAwareFutures: MyFutures,
    ws: WSClient
)(implicit ec: ExecutionContext) {
  private val tracer = GlobalOpenTelemetry.getTracer("application")

  private val logger = org.slf4j.LoggerFactory.getLogger(classOf[MyService])

  private def assertSpan()(implicit
      enclosing: Enclosing,
      line: sourcecode.Line
  ): Span = {
    assertSpan(Span.fromContextOrNull(Context.current))
  }

  private def assertSpan(
      span: Span
  )(implicit enclosing: Enclosing, line: sourcecode.Line): Span = {
    if (span == null) {
      logger.debug(
        s"We don't have a current span from ${enclosing.value} line ${line.value}!"
      )
      throw new IllegalStateException(
        s"We don't have a current span from ${enclosing.value} line ${line.value}!"
      )
    }
    logger.debug(s"assertSpan: span = ${span}")
    span
  }

  // ----------------------------------------------------------
  // The simplest case: synchronous methods.

  def getCurrentTime(implicit
      enc: sourcecode.Enclosing,
      line: sourcecode.Line
  ): Long = {
    val span = assertSpan()

    if (span.isRecording) {
      span.setAttribute("success", true)
      span.addEvent("success")
    } else {
      logger.warn(s"getCurrentTime: span is read only! ${span}")
    }

    val result = System.currentTimeMillis()
    logger.debug(s"getCurrentTime: $result")
    result
  }

  def currentTimeWithSpan: Long = {
    val span = tracer.spanBuilder("currentTime").startSpan()
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
    val span = tracer.spanBuilder("getCat").startSpan()
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
    val span =
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
    val span = tracer.spanBuilder("brokenDelayedCurrentTime").startSpan()
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
    val span = tracer.spanBuilder("fixedDelayedCurrentTime").startSpan()
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
    val span = tracer.spanBuilder("contextAwareDelayedCurrentTime").startSpan()
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
  /*
    def makeCurrent[F](f: => F)(implicit span: Span, enclosing: Enclosing, line: sourcecode.Line): F = {
      assertSpan(span)
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
   */

}
