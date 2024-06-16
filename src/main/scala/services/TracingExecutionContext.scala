package services

import io.opentelemetry.context.Context
import org.apache.pekko.actor.ActorSystem
import org.slf4j.LoggerFactory

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class TracingExecutionContext(executor: ExecutionContext, enabled: => Boolean, context: Context) extends ExecutionContextExecutor {
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

  private val enabledFlag = new AtomicBoolean(true)

  private def isEnabled: Boolean = enabledFlag.get

  def enable(): Unit = enabledFlag.set(true)

  def disable(): Unit = enabledFlag.set(false)

  def apply(actorSystem: ActorSystem) = new TracingExecutionContext(actorSystem.dispatcher, isEnabled, Context.current())

  implicit def actorSystemToTracingExecutionContext(implicit actorSystem: ActorSystem): TracingExecutionContext = {
    TracingExecutionContext(actorSystem)
  }
}
