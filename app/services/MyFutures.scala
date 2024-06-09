package services

import io.opentelemetry.context.Context
import org.apache.pekko.actor.ActorSystem

import javax.inject.Inject
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContextExecutor, Future}

// This is a stripped down version of play.api.libs.concurrent.DefaultFutures
class MyFutures @Inject()(actorSystem: ActorSystem) {

  def delayed[A](duration: FiniteDuration)(f: => Future[A]): Future[A] = {
    val context = Context.current()
    implicit val ec: ExecutionContextExecutor = new TracingExecutionContext(actorSystem.dispatcher, context)
    org.apache.pekko.pattern.after(duration, actorSystem.scheduler)(f)
  }

  class TracingExecutionContext(executor: ExecutionContextExecutor, context: Context) extends ExecutionContextExecutor {
    override def reportFailure(cause: Throwable): Unit = executor.reportFailure(cause)
    override def execute(command: Runnable): Unit = executor.execute(context.wrap(command))
  }
}
