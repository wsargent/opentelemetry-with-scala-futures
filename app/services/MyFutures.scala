package services

import io.opentelemetry.context.Context
import org.apache.pekko.actor.ActorSystem

import javax.inject.Inject
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

// This is a stripped down version of play.api.libs.concurrent.DefaultFutures
class MyFutures @Inject() (actorSystem: ActorSystem)(implicit executionContext: ExecutionContext) {
  def delayed[A](duration: FiniteDuration)(f: => Future[A]): Future[A] = {
    org.apache.pekko.pattern.after(duration, actorSystem.scheduler)(f)
  }
}
