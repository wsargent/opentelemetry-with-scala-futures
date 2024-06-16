package services

import org.apache.pekko.actor.ActorSystem

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

// This is a stripped down version of play.api.libs.concurrent.DefaultFutures
class MyFutures(implicit actorSystem: ActorSystem) {

  def delayed[A](duration: FiniteDuration)(f: => Future[A]): Future[A] = {
    implicit val ec: TracingExecutionContext = TracingExecutionContext(actorSystem)
    org.apache.pekko.pattern.after(duration, actorSystem.scheduler)(f)
  }
}
