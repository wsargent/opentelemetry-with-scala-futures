import play.api.*
import play.api.ApplicationLoader.Context
import play.api.libs.concurrent.{DefaultFutures, PekkoComponents}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.Results.*
import play.api.mvc.{DefaultActionBuilder, EssentialFilter}
import play.api.routing.Router
import play.api.routing.sird.*
import services.*

// configured from application.conf
class AppLoader extends ApplicationLoader {
  def load(context: Context) = new components.AppComponents(context).application
}
