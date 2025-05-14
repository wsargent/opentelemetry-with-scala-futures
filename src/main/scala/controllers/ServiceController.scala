package controllers

import play.api.mvc.{AbstractController, ControllerComponents}
import services.MyService

import javax.inject.Inject

final case class ServiceControllerComponents @Inject() (controllerComponents: ControllerComponents, myService: MyService)

class ServiceController(cc: ServiceControllerComponents) extends AbstractController(cc.controllerComponents) {
  def service: MyService = cc.myService
}
