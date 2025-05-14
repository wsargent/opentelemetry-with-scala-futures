package filters

import com.google.inject.Key
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import play.api.inject.Injector
import play.api.mvc.{EssentialAction, EssentialFilter, RequestHeader}
import services.{Logging, RequestScope}

import java.util.Objects.requireNonNull
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class RequestScopeFilter @Inject()(tracer: Tracer, requestScope: RequestScope, injector: Injector) extends EssentialFilter with Logging {

  override def apply(next: EssentialAction): EssentialAction = { rh =>
    val requestId = requireNonNull(rh.id.toString)
    val baggage = Baggage.builder().put("request_id", requestId).build()
    val baggageScope = baggage.storeInContext(Context.current()).makeCurrent

    // Seed the scope with the current request
    requestScope.seed(Key.get(classOf[RequestHeader]), rh)

    implicit val ec: ExecutionContext = ExecutionContext.parasitic
    next(rh).map { result =>
      requestScope.cleanupRequest(requestId)
      baggageScope.close()
      result
    }.recover {
      case e: Exception =>
        requestScope.cleanupRequest(requestId)
        baggageScope.close()
        throw e
    }
  }
}
