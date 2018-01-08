package controllers


import auth.SecurityHQAuthActions
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.ExecutionContext
import logic.{Organisation, SnykDisplay, SnykProjectIssues, Token}
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
          organisationId <- SnykDisplay.getOrganisationId(organisationResponse, organisation)
          projectResponse <- SnykDisplay.getProjects(token, organisationId, wsClient)
          projects <- SnykDisplay.getProjectIdList(projectResponse)
          vulnerabilitiesResponse <- SnykDisplay.getProjectVulnerabilities(organisationId, projects, token, wsClient)
          parsedVulnerabilitiesResponse <- SnykDisplay.parseProjectVulnerabilities(vulnerabilitiesResponse)
          results = projects.zip(parsedVulnerabilitiesResponse).map(a => a._2.withName(a._1.name).withId(a._1.id))
        } yield Ok(views.html.snyk.snyk(results))
      }
    }

  }
}



