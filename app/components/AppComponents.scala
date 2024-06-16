package components

import io.opentelemetry.context.Context
import org.slf4j.LoggerFactory
import play.api.*
import play.api.ApplicationLoader
import play.api.libs.concurrent.DefaultFutures
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.Results.*
import play.api.mvc.*
import play.api.routing.Router
import play.api.routing.sird.*
import services.*

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{ExecutionContext, Future}

class AppComponents(context: ApplicationLoader.Context) extends BuiltInComponentsFromContext(context) with AhcWSComponents {
  private val logger = LoggerFactory.getLogger(getClass)

  // If we uncomment this, all the tests fail.
  //override implicit lazy val executionContext: ExecutionContext = TracingExecutionContext(actorSystem)

  val service = new MyService(new DefaultFutures(actorSystem), contextAwareFutures = new MyFutures()(actorSystem), ws = wsClient)

  override val httpFilters: Seq[EssentialFilter] = Nil

  override val router: Router = Router.from {
    case POST(p"/enable") =>
      Action {
        TracingExecutionContext.enable()
        Redirect("/")
      }

    case POST(p"/disable") =>
      Action {
        TracingExecutionContext.disable()
        Redirect("/")
      }

    case GET(p"/") =>
      Action {
        Ok(views.html.index())
      }

    case GET(p"/sync") =>
      Action {
        val result = service.currentTimeWithSpan
        Ok(result.toString)
      }

    case GET(p"/future") =>
      Action.async {
        service.futureCurrentTime.map { result =>
          Ok(result.toString)
        }
      }

    case GET(p"/broken") =>
      Action.async {
        service.brokenDelayedCurrentTime.map { result =>
          Ok(result.toString)
        }
      }

    case GET(p"/fixed") =>
      Action.async {
        service.fixedDelayedCurrentTime.map { result =>
          Ok(result.toString)
        }
      }

    case GET(p"/context") =>
      Action.async {
        service.contextAwareDelayedCurrentTime.map { result =>
          Ok(result.toString)
        }
      }

    case GET(p"/cat") =>
      Action.async {
        service.getCat.map { bytes =>
          Ok(bytes).as("image/jpeg")
        }
      }
  }

}
