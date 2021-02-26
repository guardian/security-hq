package controllers

import auth.SecurityHQAuthActions
import com.gu.googleauth.GoogleAuthConfig
import model.GcpReport
import org.joda.time.DateTime
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
      for {
        allGcpFindings <- cacheService.getGcpFindings
        gcpProjectToFinding = allGcpFindings.groupBy(_.project)
        report = GcpReport(DateTime.now, gcpProjectToFinding)
      } yield Ok(views.html.gcp.gcp(report))
    }
  }
}
