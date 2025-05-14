package services

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.{Span, StatusCode, Tracer}
import io.opentelemetry.context.Context

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object Utils {
  val tracer: Tracer = GlobalOpenTelemetry.getTracer("application")

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
    }(using ExecutionContext.parasitic)
    f
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
}
