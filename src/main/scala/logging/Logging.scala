package logging

import com.tersesystems.echopraxia.api.Value
import com.tersesystems.echopraxia.plusscala.api.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import services.TracingExecutionContext

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.{ClassTag, classTag}

trait Logging extends LoggingBase with FutureToValueImplicits {
  implicit def futureToName[TV: ToValue : ClassTag]: ToName[Future[TV]] = _ => s"future[${classTag[TV].runtimeClass.getName}]"

  implicit def classToField: ToField[Class[_]] = ToField(_ => "class_name", clazz => ToValue(clazz.getName))

  implicit def contextToField: ToField[Context] = ToField(_ => "otel_context", { other =>
      ToObjectValue(
        other.getClass,
        "to_string" -> Value.string(other.toString).abbreviateAfter(20)
      )
  })

  implicit def spanToField: ToField[Span] = ToField(_ => "otel_span", { span =>
    ToObjectValue(
      span.getClass,
      "to_string" -> Value.string(span.toString).abbreviateAfter(20)
    )
  })

  implicit def executorToField: ToField[ExecutionContext] = ToField(_ => "execution_context", {
    case tctx: TracingExecutionContext =>
      ToObjectValue(
        tctx.getClass,
        "wrapped_executor" -> tctx.executor,
        tctx.context
      )
    case other =>
      ToObjectValue(
        other.getClass,
        "to_string" -> Value.string(other.toString).abbreviateAfter(20)
      )
  })
}
