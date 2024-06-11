package services

import io.opentelemetry.context.Context

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class TracingExecutionContext(executor: ExecutionContext, context: Context) extends ExecutionContextExecutor {
  override def reportFailure(cause: Throwable): Unit = executor.reportFailure(cause)
  override def execute(command: Runnable): Unit = executor.execute(context.wrap(command))
}

object TracingExecutionContext {
  def apply()(implicit ec: ExecutionContext): TracingExecutionContext = apply(Context.current())
  def apply(context: Context)(implicit ec: ExecutionContext): TracingExecutionContext = new TracingExecutionContext(ec, context)
}