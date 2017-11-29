package controllers

import play.api.mvc._


class UtilityController()(implicit val controllerComponents: ControllerComponents) extends BaseController {
  def healthcheck() = Action {
    Ok("ok")
  }
}
