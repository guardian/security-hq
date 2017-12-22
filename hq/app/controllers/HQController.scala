package controllers

import auth.SecurityHQAuthActions
import aws.AWS
import aws.iam.IAMClient
import config.Config
import model.AwsAccount
import play.api._
import play.api.libs.ws.WSClient
import play.api.mvc._
import service.CredentialsService
import service.CredentialsService.ExposedKeys
import utils.attempt.PlayIntegration.attempt
import utils.attempt.{Attempt, Failure}

import scala.concurrent.ExecutionContext

class HQController (val config: Configuration)
                   (implicit val ec: ExecutionContext, val wsClient: WSClient, val bodyParser: BodyParser[AnyContent], val controllerComponents: ControllerComponents, val assetsFinder: AssetsFinder)
  extends BaseController  with SecurityHQAuthActions {

  private val accounts = Config.getAwsAccounts(config)

  def index = authAction {
    Ok(views.html.index(accounts))
  }

  def iam = authAction.async {
    attempt {
      for {
        accountsAndReports <- CredentialsService.getCredentialReport()
      } yield Ok(views.html.iam.iam(accountsAndReports.toMap))

    }
  }

  def iamAccount(accountId: String) = authAction.async {

    def findExposedKeys(account: AwsAccount)(exposedKeys: ExposedKeys) = {
      val opt = exposedKeys.filter {
        case Right((acc, _)) => acc == account
        case Left(_) => false
      }
      opt.headOption.map(e => e.map(_._2.flaggedResources)).map(Attempt.Right).getOrElse(Attempt.Left(Failure.cannotExposedKeys(accountId)))
    }

    def findCredReport(account: AwsAccount)(reports: CredentialsService.CredentialReport) = {
      val filtered = reports.filter { case (acc, _) => acc == account }
      filtered.headOption.map(e => Attempt.Right(e._2)).getOrElse(Attempt.Left(Failure.cannotGetCredentialReports(accountId)))
    }

    attempt {
      for {
        account <- AWS.lookupAccount(accountId, accounts)
        exposedIamKeys <-  CredentialsService.getExposedKeys() flatMap findExposedKeys(account)
        credReport <- CredentialsService.getCredentialReport() flatMap findCredReport(account)
      } yield Ok(views.html.iam.iamAccount(account, exposedIamKeys, credReport))
    }
  }

}

