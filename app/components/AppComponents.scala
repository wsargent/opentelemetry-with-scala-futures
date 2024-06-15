package components

import play.api.*
import play.api.ApplicationLoader.Context
import play.api.libs.concurrent.DefaultFutures
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.Results.*
import play.api.mvc.{DefaultActionBuilder, EssentialFilter}
import play.api.routing.Router
import play.api.routing.sird.*
import services.*

class AppComponents(context: Context)
    extends BuiltInComponentsFromContext(context)
    with AhcWSComponents {

  val futures = new DefaultFutures(actorSystem)
  val myFutures = new MyFutures(actorSystem)
  val service =
    new MyService(futures, contextAwareFutures = myFutures, ws = wsClient)

  override val httpFilters: Seq[EssentialFilter] = Nil

  override val Action: DefaultActionBuilder = defaultActionBuilder

  override val router: Router = Router.from {
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
