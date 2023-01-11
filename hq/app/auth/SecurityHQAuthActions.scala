package auth

import com.gu.googleauth.{AuthAction, GoogleAuthConfig, GoogleGroupChecker, LoginSupport}
import config.Config
import controllers.routes
import play.api.Configuration
import play.api.mvc.{AnyContent, BodyParser, Call}

import scala.concurrent.ExecutionContext


trait SecurityHQAuthActions extends LoginSupport {

  implicit val config: Configuration
  implicit val bodyParser: BodyParser[AnyContent]
  implicit val ec: ExecutionContext

  val loginTarget: Call = routes.AuthController.login
  override val failureRedirectTarget: Call = routes.AuthController.loginError
  override val defaultRedirectTarget: Call = routes.HQController.index
  override val authConfig: GoogleAuthConfig

  val googleGroupChecker: GoogleGroupChecker = Config.googleGroupChecker
  val requiredGoogleGroups: Set[String] = Set(Config.twoFAGroup, Config.departmentGroup)
  val authAction = new AuthAction[AnyContent](authConfig, loginTarget, bodyParser)(ec)

}
