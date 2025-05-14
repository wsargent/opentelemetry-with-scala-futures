package controllers

import io.opentelemetry.api.trace.Span

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class HomeController @Inject() (cc: ServiceControllerComponents, indexTemplate: views.html.index) extends ServiceController(cc) {

  def index = Action {
    // Use dependency injection of the template here, and do not include the request
    // We can use RequestLookup to get the current request from baggage
    Ok(indexTemplate())
  }

  def sync = Action {
    val result = service.currentTimeWithSpan
    Ok(result.toString)
  }

  def future = Action.async {
    service.futureCurrentTime.map { result =>
      Ok(result.toString)
    }(using ExecutionContext.parasitic)
  }

  def cat = Action.async {
    service.getCat.map { bytes =>
      Ok(bytes).as("image/jpeg")
    }(using ExecutionContext.parasitic)
  }
}
