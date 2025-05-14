import com.google.inject.{AbstractModule, Scopes}
import io.opentelemetry.api.trace.Tracer
import play.api.{Configuration, Environment}
import services.{RequestLookup, RequestScope, RequestScoped, Utils}

class Module(environment: Environment, configuration: Configuration)
  extends AbstractModule {

  override def configure() = {
    bind(classOf[Tracer]).toInstance(Utils.tracer)

    // Bind the RequestScope as a singleton
    bind(classOf[RequestScope]).in(Scopes.SINGLETON)

    // Bind the scope itself
    bindScope(classOf[RequestScoped], new RequestScope())

    bind(classOf[RequestLookup]).in(classOf[RequestScoped])
  }
}
