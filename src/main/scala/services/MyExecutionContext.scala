package services

import org.apache.pekko.actor.ActorSystem
import play.api.libs.concurrent.CustomExecutionContext

import javax.inject.Inject

// https://www.playframework.com/documentation/2.9.x/ThreadPools#Using-other-thread-pools
class MyExecutionContext @Inject()(actorSystem: ActorSystem) extends CustomExecutionContext(actorSystem, "my.executor")