package controllers


import auth.SecurityHQAuthActions
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.ExecutionContext
import logic.{Organisation, SnykDisplay, Token}
import utils.attempt.PlayIntegration.attempt


class SnykController(val config: Configuration)
                    (implicit val ec: ExecutionContext, val wsClient: WSClient, val bodyParser: BodyParser[AnyContent], val controllerComponents: ControllerComponents, val assetsFinder: AssetsFinder)
  extends BaseController  with SecurityHQAuthActions
{


  def snyk() = {
    val token = new Token(config.get[String]("hq.snyk_token"))
    val organisation = new Organisation(config.get[String]("hq.organisation"))
    Action.async {
      attempt {
        for {
          organisationResponse <- SnykDisplay.getSnykOrganisations(token, wsClient)
          organisationId <- SnykDisplay.getOrganisationId(organisationResponse.body, organisation)
          projectResponse <- SnykDisplay.getProjects(token, organisationId, wsClient)
          projects <- SnykDisplay.getProjectIdList(projectResponse.body)
          vulnerabilitiesResponse <- SnykDisplay.getProjectVulnerabilities(organisationId, projects, token, wsClient)
          vulnerabilitiesResponseBodies = vulnerabilitiesResponse.map(a => a.body)
          parsedVulnerabilitiesResponse <- SnykDisplay.parseProjectVulnerabilities(vulnerabilitiesResponseBodies)
          results = SnykDisplay.labelProjects(projects, parsedVulnerabilitiesResponse)
        } yield Ok(views.html.snyk.snyk(results))
      }
    }

  }
}



