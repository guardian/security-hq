package controllers

import auth.SecurityHQAuthActions
import aws.AWS
import aws.inspector.Inspector
import com.amazonaws.regions.Regions
import com.gu.googleauth.GoogleAuthConfig
import config.Config
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.mvc._
import utils.attempt.Attempt
import utils.attempt.PlayIntegration.attempt

import scala.concurrent.ExecutionContext

class InspectorController(val config: Configuration,
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

  def inspector = authAction.async {
    attempt {
      for {
        accountAssessmentRuns <- Attempt.labelledTaverseWithFailures(accounts)(Inspector.inspectorRuns)
      } yield Ok(views.html.inspector.inspector(accountAssessmentRuns))
    }
  }

  def inspectorAccount(accountId: String) = authAction.async {
    attempt {
      for {
        account <- AWS.lookupAccount(accountId, accounts)
        assessmentRuns <- Inspector.inspectorRuns(account)
      } yield Ok(views.html.inspector.inspectorAccount(assessmentRuns, account))
    }
  }
}
