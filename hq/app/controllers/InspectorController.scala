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

  def inspectorAccount(accountId: String) = authAction.async {
    attempt {
      for {
        account <- AWS.lookupAccount(accountId, accounts)
        inspectorClient = Inspector.client(account, Regions.EU_WEST_1)
        inspectorRunArns <- Inspector.listInspectorRuns(inspectorClient)
        assessmentRuns <- Inspector.describeInspectorRuns(inspectorRunArns, inspectorClient)
        processedAssessmentRuns = InspectorResults.relevantRuns(assessmentRuns)
      } yield Ok(views.html.inspector.inspectorAccount(processedAssessmentRuns, account))
    }
  }
}
