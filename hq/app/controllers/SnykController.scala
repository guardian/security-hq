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
          organisations <- SnykDisplay.parseOrganisations(organisationResponse.body, requiredOrganisation)

          projectResponses <- Snyk.getProjects(token, organisations, wsClient)
          projectResponseBodies = projectResponses.map{ case (organisation,response) => (organisation, response)}

          organisationAndProjects <- SnykDisplay.getProjectIdList(projectResponseBodies)
          labelledProjects = SnykDisplay.labelOrganisations(organisationAndProjects)

          vulnerabilitiesResponse <- Snyk.getProjectVulnerabilities(labelledProjects, token, wsClient)
          vulnerabilitiesResponseBodies = vulnerabilitiesResponse.map(a => a.body)

          parsedVulnerabilitiesResponse <- SnykDisplay.parseProjectVulnerabilities(vulnerabilitiesResponseBodies)

          results = SnykDisplay.labelProjects(labelledProjects, parsedVulnerabilitiesResponse)
          sortedResult = SnykDisplay.sortProjects(results)
        } yield Ok(views.html.snyk.snyk(sortedResult))
      }
    }

  }

  def getSnykToken: Attempt[SnykToken] = configraun.getAsString("/snyk/token") match {
    case Left(error) =>
      val failure = Failure(error.message, "Could not read Snyk token from aws parameter store", 500, None, Some(error.e))
      Attempt.Left(failure)
    case Right(token) => Attempt.Right(SnykToken(token))
  }

  def getSnykOrganisation: Attempt[SnykGroupId] = configraun.getAsString("/snyk/organisation") match {
    case Left(error) =>
      val failure = Failure(error.message, "Could not read Snyk organisation from aws parameter store", 500, None, Some(error.e))
      Attempt.Left(failure)
    case Right(groupId) => Attempt.Right(SnykGroupId(groupId))
  }

}