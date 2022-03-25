package controllers


import auth.SecurityHQAuthActions
import com.gu.googleauth.GoogleAuthConfig
import logic.SnykDisplay
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.CacheService
import utils.attempt.PlayIntegration.attempt

import scala.concurrent.ExecutionContext

class SnykController(val config: Configuration,
                     val cacheService: CacheService,
                     val authConfig: GoogleAuthConfig
                     )
                    (implicit
                     val ec: ExecutionContext,
                     val wsClient: WSClient,
                     val bodyParser: BodyParser[AnyContent],
                     val controllerComponents: ControllerComponents,
                     val assetsFinder: AssetsFinder)
  extends BaseController  with SecurityHQAuthActions
{

  def snyk =  authAction.async {
    attempt {
      for {
        accountAssessmentRuns <- cacheService.getAllSnykResults
        sorted = SnykDisplay.sortOrgs(accountAssessmentRuns)
      } yield Ok(views.html.snyk.snyk(sorted))
    }
  }

}