package components

import io.opentelemetry.api.trace.Span
import play.api._
import play.api.libs.concurrent.DefaultFutures
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.Results._
import play.api.mvc._
import play.api.routing.Router
import play.api.routing.sird._
import services._

class AppComponents(context: ApplicationLoader.Context) extends BuiltInComponentsFromContext(context) with AhcWSComponents {
  // If we uncomment this, all the tests fail.
  // override implicit lazy val executionContext: ExecutionContext = TracingExecutionContext(actorSystem)

  val service = new MyService(
    futures = new DefaultFutures(actorSystem),
    contextAwareFutures = new MyFutures()(actorSystem),
    ws = wsClient,
    myExecutionContext = new MyExecutionContext(actorSystem)
  )

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
        implicit val span = Span.current
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
