package services

import com.tersesystems.echopraxia.plusscala.LoggerFactory
import com.tersesystems.echopraxia.plusscala.api.PresentationFieldBuilder
import io.opentelemetry.context.Context
import logging.Logging
import org.apache.pekko.actor.ActorSystem
import services.TracingExecutionContext.logger

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class TracingExecutionContext(val executor: ExecutionContext, enabled: => Boolean, val context: Context) extends ExecutionContextExecutor with Logging {
  logger.debug(s"constructor: {} {}", executor, context)

  override def reportFailure(cause: Throwable): Unit =
    executor.reportFailure(cause)

  override def execute(command: Runnable): Unit = {
    if (enabled) {
      val c = context
      logger.debug(s"execute: wrapping with context {}", context)
      executor.execute(c.wrap(command))
    } else {
      executor.execute(command)
    }
  }
}

object TracingExecutionContext {
  private val logger = LoggerFactory.getLogger(classOf[TracingExecutionContext], PresentationFieldBuilder)

  private val enabledFlag = new AtomicBoolean(true)

  private def isEnabled: Boolean = enabledFlag.get

  def enable(): Unit = enabledFlag.set(true)

  def disable(): Unit = enabledFlag.set(false)

  def apply(actorSystem: ActorSystem): TracingExecutionContext = apply(actorSystem.dispatcher)

  def apply(executionContext: ExecutionContext): TracingExecutionContext = {
    executionContext match {
      case tctx: TracingExecutionContext => tctx
      case _                             => new TracingExecutionContext(executionContext, isEnabled, Context.current())
    }
  }

  implicit def actorSystemToTracingExecutionContext(implicit actorSystem: ActorSystem): TracingExecutionContext = TracingExecutionContext(actorSystem)
}
