package controllers

import auth.SecurityHQAuthActions
import com.gu.googleauth.GoogleAuthConfig
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.mvc.{AnyContent, BaseController, BodyParser, ControllerComponents}
import services.CacheService
import utils.attempt.PlayIntegration.attempt

import scala.concurrent.ExecutionContext

class GcpController(val config: Configuration, val authConfig: GoogleAuthConfig, val cacheService: CacheService)
  (implicit val ec: ExecutionContext, val wsClient: WSClient, val controllerComponents: ControllerComponents, val bodyParser: BodyParser[AnyContent], val assetsFinder: AssetsFinder)
  extends BaseController with SecurityHQAuthActions {

  def all = authAction.async {
    attempt {
      cacheService.getGcpReport.map(report => Ok(views.html.gcp.gcp(report)))
    }
  }
}
