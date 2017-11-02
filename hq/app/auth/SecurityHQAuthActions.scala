package auth

import com.gu.googleauth.{GoogleAuthConfig, Actions => GoogleAuthActions}
import config.Config
import controllers.routes
import play.api.Configuration
import play.api.mvc.Call


trait SecurityHQAuthActions extends GoogleAuthActions {
  implicit val config: Configuration

  override val loginTarget: Call = routes.AuthController.login()
  override val failureRedirectTarget: Call = routes.AuthController.loginError()
  override val defaultRedirectTarget: Call = routes.HQController.index()

  override val authConfig: GoogleAuthConfig = Config.googleSettings

  val googleGroupChecker = Config.googleGroupChecker
  val requiredGoogleGroups = Set(Config.twoFAGroup)
}
