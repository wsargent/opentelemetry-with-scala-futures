package controllers

import io.opentelemetry.api.GlobalOpenTelemetry
import play.api.mvc.*
import services.MyService

import javax.inject.*
import scala.concurrent.ExecutionContext

@Singleton
class HomeController @Inject()(service: MyService, cc: ControllerComponents)(implicit ec: ExecutionContext) extends AbstractController(cc) {
  private val tracer = GlobalOpenTelemetry.getTracer("application")

  def index = Action {
    Ok(views.html.index())
  }
  
  def cat: Action[AnyContent] = Action.async {
    service.getCat.map { bytes =>
      Ok(bytes).as("image/jpeg")
    }
  }

  def syncGet: Action[AnyContent] = Action {
    val result = service.currentTimeWithSpan
    Ok(result.toString)
  }

  def futureGet: Action[AnyContent] = Action.async {
    // Needs to be explicit span if we have async?
    val span = tracer.spanBuilder("futureGet").startSpan
    val scope = span.makeCurrent()
    try {
      service.futureCurrentTime.map { result =>
        Ok(result.toString)
      }
    } finally {
      scope.close()
    }
  }

  def brokenGet: Action[AnyContent] = Action.async {
    // Needs to be explicit span if we have async?
    val span = tracer.spanBuilder("brokenGet").startSpan
    val scope = span.makeCurrent()
    try {
      service.brokenDelayedCurrentTime.map { result =>
        Ok(result.toString)
      }
    } finally {
      scope.close()
    }
  }

  def fixedGet: Action[AnyContent] = Action.async {
    // Needs to be explicit span if we have async?
    val span = tracer.spanBuilder("fixedGet").startSpan
    val scope = span.makeCurrent()
    try {
      service.fixedDelayedCurrentTime.map { result =>
        Ok(result.toString)
      }
    } finally {
      scope.close()
    }
  }

  def contextGet: Action[AnyContent] = Action.async {
    val span = tracer.spanBuilder("contextGet").startSpan
    val scope = span.makeCurrent()
    try {
      service.contextAwareDelayedCurrentTime.map { result =>
        Ok(result.toString)
      }
    } finally {
      scope.close()

    }


}