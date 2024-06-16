import play.api._
import play.api.ApplicationLoader.Context

// configured from application.conf
class AppLoader extends ApplicationLoader {
  def load(context: Context) = {
    LoggerConfigurator(context.environment.classLoader).foreach(_.configure(context.environment))
    new components.AppComponents(context).application
  }
}
