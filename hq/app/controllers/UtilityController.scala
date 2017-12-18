package controllers

import play.api.mvc._

class UtilityController()(implicit val controllerComponents: ControllerComponents, val assetsFinder: AssetsFinder) extends BaseController {
  def healthcheck() = Action {
    Ok("ok")
  }
  def documentation(file: String) = Action {
    Ok(views.html.doc(file))
  }

}
