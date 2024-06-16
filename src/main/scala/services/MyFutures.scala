package services

import org.apache.pekko.actor.ActorSystem

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

// This is a stripped down version of play.api.libs.concurrent.DefaultFutures
class MyFutures(implicit actorSystem: ActorSystem) {
  import TracingExecutionContext.*

  def delayed[A](duration: FiniteDuration)(f: => Future[A]): Future[A] = {
    org.apache.pekko.pattern.after(duration, actorSystem.scheduler)(f)
  }
}
