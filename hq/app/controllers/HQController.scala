package controllers

import auth.SecurityHQAuthActions
import com.gu.googleauth.GoogleAuthConfig
import config.Config
import logic.DocumentUtil
import play.api._
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.ExecutionContext


class HQController(val config: Configuration, val authConfig: GoogleAuthConfig)
                  (implicit val ec: ExecutionContext, val wsClient: WSClient, val bodyParser: BodyParser[AnyContent], val controllerComponents: ControllerComponents, val assetsFinder: AssetsFinder)
  extends BaseController with SecurityHQAuthActions {

  private val accounts = Config.getAwsAccounts(config)
  private val iamReportLink = "https://metrics.gutools.co.uk/d/bdn97cui5rbi8f/iam-credentials-report"

  def index = authAction {

    Ok(views.html.index(accounts, iamReportLink))
  }

  def healthcheck = Action {
    Ok("ok")
  }

  def documentationHome = Action {
    Ok(views.html.documentationHome())
  }

  def documentation(file: String) = Action {
    // If the .md file extension is present, Play does not route the request correctly
    val fileRefWithoutMdExtension = file.stripSuffix(".md")

    DocumentUtil.convert(fileRefWithoutMdExtension) match {
      case Some(rendered) =>
        Ok(views.html.doc(rendered))
      case None =>
        NotFound(views.html.documentation.unknown())
    }
  }
}
