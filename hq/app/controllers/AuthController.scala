package controllers

import auth.SecurityHQAuthActions
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.{Configuration, Environment}

import scala.concurrent.ExecutionContext


class AuthController(environment: Environment, val config: Configuration)
                    (implicit val ec: ExecutionContext, val wsClient: WSClient, val bodyParser: BodyParser[AnyContent], val controllerComponents: ControllerComponents)
  extends BaseController with SecurityHQAuthActions {

  implicit val mode = environment.mode

  def loginError = Action { implicit request =>
    val error = request.flash.get("error").getOrElse("There was an error logging in")
    Ok(views.html.loginError(error))

  }

  def login = Action.async { implicit request =>
    startGoogleLogin()
  }

  def logout = Action { implicit request =>
    Redirect(routes.HQController.index()).withNewSession
  }

  def oauthCallback = Action.async { implicit request =>
    processOauth2Callback(requiredGoogleGroups, googleGroupChecker)
  }
}
