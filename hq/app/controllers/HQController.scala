package controllers

import auth.SecurityHQAuthActions
import aws.AWS
import com.gu.googleauth.GoogleAuthConfig
import config.Config
import logic.ReportDisplay.{exposedKeysSummary, sortAccountsByReportSummary}
import play.api._
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.CacheService
import utils.attempt.PlayIntegration.attempt

import scala.concurrent.ExecutionContext

class HQController(val config: Configuration, val authConfig: GoogleAuthConfig)
                  (implicit val ec: ExecutionContext, val wsClient: WSClient, val bodyParser: BodyParser[AnyContent], val controllerComponents: ControllerComponents, val assetsFinder: AssetsFinder)
  extends BaseController  with SecurityHQAuthActions {

  private val accounts = Config.getAwsAccounts(config)

  def index = authAction {
    Ok(views.html.index(accounts))
  }

  def healthcheck() = Action {
    Ok("ok")
  }
  def documentation(file: String) = Action {
    Ok(views.html.doc(file))
  }
}
