package controllers

import auth.SecurityHQAuthActions
import aws.AWS
import aws.inspector.Inspector
import com.amazonaws.regions.Regions
import com.gu.googleauth.GoogleAuthConfig
import config.Config
import logic.InspectorResults
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.CacheService
import utils.attempt.Attempt
import utils.attempt.PlayIntegration.attempt

import scala.concurrent.ExecutionContext

class InspectorController(val config: Configuration,
                          val cacheService: CacheService,
                          val authConfig: GoogleAuthConfig)
                         (implicit
                          val ec: ExecutionContext,
                          val wsClient: WSClient,
                          val bodyParser: BodyParser[AnyContent],
                          val controllerComponents: ControllerComponents,
                          val assetsFinder: AssetsFinder)
  extends BaseController with SecurityHQAuthActions
{
  // hard-coded for now
  val region = Regions.EU_WEST_1

  private val accounts = Config.getAwsAccounts(config)

  def inspector = authAction {
    val accountAssessmentRuns = cacheService.getAllInspectorResults()
    val sorted = InspectorResults.sortAccountResults(accountAssessmentRuns.toList)
    Ok(views.html.inspector.inspector(sorted))
  }

  def inspectorAccount(accountId: String) = authAction.async {
    attempt {
      for {
        account <- AWS.lookupAccount(accountId, accounts)
        assessmentRuns <- Attempt.fromEither(cacheService.getInspectorResultsForAccount(account))
      } yield Ok(views.html.inspector.inspectorAccount(assessmentRuns, account))
    }
  }

  def refresh() = authAction {
    cacheService.refreshInspectorBox()
    Ok("Refreshing AWS inspector info")
  }
}
