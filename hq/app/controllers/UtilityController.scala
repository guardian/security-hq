package controllers

import play.api.mvc._


class UtilityController() extends Controller {
  def healthcheck() = Action {
    Ok("ok")
  }
}
