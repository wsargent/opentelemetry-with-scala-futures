package controllers

import play.api.mvc._
import services.MyService

import javax.inject._
import scala.concurrent.ExecutionContext

@Singleton
class HomeController @Inject()(service: MyService, cc: ControllerComponents)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  def index = Action {
    Ok(views.html.index())
  }

  def syncGet: Action[AnyContent] = Action {
    val result = service.currentTimeWithSpan
    Ok(result.toString)
  }

  def futureGet: Action[AnyContent] = Action.async {
    service.futureCurrentTime.map { result =>
      Ok(result.toString)
    }
  }

  def brokenGet: Action[AnyContent] = Action.async {
    service.brokenDelayedCurrentTime.map { result =>
      Ok(result.toString)
    }
  }

  def fixedGet: Action[AnyContent] = Action.async {
    service.fixedDelayedCurrentTime.map { result =>
      Ok(result.toString)
    }
  }

  def contextGet: Action[AnyContent] = Action.async {
    service.contextAwareDelayedCurrentTime.map { result =>
      Ok(result.toString)
    }
  }


}