package controllers

import auth.SecurityHQAuthActions
import com.gu.googleauth.GoogleAuthConfig
import play.api.libs.ws.WSClient
import play.api.mvc.*
import play.api.{Configuration, Environment, Mode}

import scala.concurrent.ExecutionContext

class AuthController(environment: Environment, val config: Configuration, val authConfig: GoogleAuthConfig)
                    (implicit val ec: ExecutionContext, val wsClient: WSClient, val bodyParser: BodyParser[AnyContent], val controllerComponents: ControllerComponents, val assetsFinder: AssetsFinder)
  extends BaseController with SecurityHQAuthActions {

  implicit val mode: Mode = environment.mode

  def loginError: Action[AnyContent] = Action { implicit request =>
    val error = request.flash.get("error").getOrElse("There was an error logging in")
    Ok(views.html.loginError(error))
  }

  def login: Action[AnyContent] = Action.async { implicit request =>
    startGoogleLogin()
  }

  def logout: Action[AnyContent] = Action { implicit request =>
    Redirect(routes.HQController.index).withNewSession
  }

  def oauthCallback: Action[AnyContent] = Action.async { implicit request =>
    processOauth2Callback(requiredGoogleGroups, googleGroupChecker)
  }
}
