package controllers

import auth.SecurityHQAuthActions
import play.api.{Configuration, Environment}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Controller}


class AuthController(environment: Environment)
  (implicit val wsClient: WSClient, val config: Configuration)
  extends Controller with SecurityHQAuthActions {

  implicit val mode = environment.mode

  def login = Action.async { implicit request =>
    startGoogleLogin()
  }

  def loginError = Action { implicit request =>
    val error = request.flash.get("error").getOrElse("There was an error logging in")
    ???
  }

  def logout = Action { implicit request =>
    Redirect(routes.HQController.index()).withNewSession
  }

  def oauthCallback = Action.async { implicit request =>
    processOauth2Callback(requiredGoogleGroups, googleGroupChecker)
  }
}
