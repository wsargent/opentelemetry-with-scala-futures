package services

import io.opentelemetry.context.Context
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class TracingExecutionContext(executor: ExecutionContext, enabled: => Boolean, context: => Context) extends ExecutionContextExecutor {
  override def reportFailure(cause: Throwable): Unit =
    executor.reportFailure(cause)
  override def execute(command: Runnable): Unit = {
    if (enabled) {
      val c = context
      TracingExecutionContext.logger.debug(s"execute: wrapping with context $c")
      executor.execute(c.wrap(command))
    } else {
      executor.execute(command)
    }
  }
}

object TracingExecutionContext {
  private val logger = LoggerFactory.getLogger(classOf[ExecutionContext])
}
