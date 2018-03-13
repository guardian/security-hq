package controllers


import auth.SecurityHQAuthActions
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.ExecutionContext
import logic.SnykDisplay
import api.Snyk
import com.gu.configraun.Errors.ConfigraunError
import com.gu.googleauth.GoogleAuthConfig
import utils.attempt.{Attempt, Failure}
import utils.attempt.PlayIntegration.attempt
import model._

class SnykController(val config: Configuration, val configraun: com.gu.configraun.models.Configuration,
                     val authConfig: GoogleAuthConfig
//                     ,
//                     val snykWsClient: WSClient
                     )
                    (implicit
                     val ec: ExecutionContext,
                     val wsClient: WSClient,
                     val bodyParser: BodyParser[AnyContent],
                     val controllerComponents: ControllerComponents,
                     val assetsFinder: AssetsFinder)
  extends BaseController  with SecurityHQAuthActions
{

  def snyk: Action[AnyContent] = {

    Action.async {
      attempt {
        for {

          token <- getSnykToken

          requiredOrganisation <- getSnykOrganisation

          organisationResponse <- Snyk.getSnykOrganisations(token, wsClient)
//          organisationResponse <- Snyk.getSnykOrganisations(token, snykWsClient)
          organisations <- SnykDisplay.parseOrganisations(organisationResponse.body, requiredOrganisation)

          projectResponses <- Snyk.getProjects(token, organisations, wsClient)
//          projectResponses <- Snyk.getProjects(token, organisations, snykWsClient)
          projectResponseBodies = projectResponses.map{ case (organisation,response) => (organisation, response.body)}

          organisationAndProjects <- SnykDisplay.getProjectIdList(projectResponseBodies)
          labelledProjects = SnykDisplay.labelOrganisations(organisationAndProjects)

          vulnerabilitiesResponse <- Snyk.getProjectVulnerabilities(labelledProjects, token, wsClient)
//          vulnerabilitiesResponse <- Snyk.getProjectVulnerabilities(labelledProjects, token, snykWsClient)
          vulnerabilitiesResponseBodies = vulnerabilitiesResponse.map(a => a.body)

          parsedVulnerabilitiesResponse <- SnykDisplay.parseProjectVulnerabilities(vulnerabilitiesResponseBodies)

          results = SnykDisplay.labelProjects(labelledProjects, parsedVulnerabilitiesResponse)
          sortedResult = SnykDisplay.sortProjects(results)
        } yield Ok(views.html.snyk.snyk(sortedResult))
      }
    }

  }

  def getSnykToken: Attempt[SnykToken] = configraun.getAsString("/snyk/token") match {
    case Left(a:ConfigraunError) =>
      val failure = Failure(a.message, "Could not read Snyk token from aws parameter store", 500, None, Some(a.e))
      Attempt.Left(failure)
    case Right(a:String) => Attempt.Right(SnykToken(a))
  }

  def getSnykOrganisation: Attempt[SnykGroupId] = configraun.getAsString("/snyk/organisation") match {
    case Left(a:ConfigraunError) =>
      val failure = Failure(a.message, "Could not read Snyk organisation from aws parameter store", 500, None, Some(a.e))
      Attempt.Left(failure)
    case Right(a:String) => Attempt.Right(SnykGroupId(a))
  }

}