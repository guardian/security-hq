package controllers


import auth.SecurityHQAuthActions
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.ExecutionContext
import logic.SnykDisplay
import api.Snyk
import com.gu.configraun.Errors.ConfigraunError
import utils.attempt.{Attempt, Failure}
import utils.attempt.PlayIntegration.attempt
import model._

class SnykController(val config: Configuration, val configraun: com.gu.configraun.models.Configuration)
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
          organisation <- SnykDisplay.getOrganisation(organisationResponse.body, requiredOrganisation)

          projectResponse <- Snyk.getProjects(token, organisation.id, wsClient)
          projects <- SnykDisplay.getProjectIdList(projectResponse.body)
          labelledProjects = SnykDisplay.labelOrganisations(projects, organisation)

          vulnerabilitiesResponse <- Snyk.getProjectVulnerabilities(organisation.id, projects, token, wsClient)
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

  def getSnykOrganisation: Attempt[SnykOrganisationName] = configraun.getAsString("/snyk/organisation") match {
    case Left(a:ConfigraunError) =>
      val failure = Failure(a.message, "Could not read Snyk organisation from aws parameter store", 500, None, Some(a.e))
      Attempt.Left(failure)
    case Right(a:String) => Attempt.Right(SnykOrganisationName(a))
  }

}



