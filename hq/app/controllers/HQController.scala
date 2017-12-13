package controllers

import auth.SecurityHQAuthActions
import aws.AWS
import aws.ec2.EC2
import aws.iam.IAMClient
import aws.support.{TrustedAdvisor, TrustedAdvisorExposedIAMKeys}
import config.Config
import play.api._
import play.api.libs.ws.WSClient
import play.api.mvc._
import utils.attempt.PlayIntegration.attempt

import scala.concurrent.ExecutionContext

class HQController (val config: Configuration)
                   (implicit val ec: ExecutionContext, val wsClient: WSClient, val bodyParser: BodyParser[AnyContent], val controllerComponents: ControllerComponents, val assetsFinder: AssetsFinder)
  extends BaseController  with SecurityHQAuthActions {

  private val accounts = Config.getAwsAccounts(config)

  def index = authAction {
    Ok(views.html.index(accounts))
  }

  def accountHub(accountId: String) = authAction.async {
    attempt {
      for {
        account <- AWS.lookupAccount(accountId, accounts)
        client = TrustedAdvisor.client(account)
        exposedIamKeysResult <- TrustedAdvisorExposedIAMKeys.getExposedIAMKeys(client)
        exposedIamKeys = exposedIamKeysResult.flaggedResources
        credReport <- IAMClient.getCredentialsReport(account)
        flaggedSgs <- EC2.flaggedSgsForAccount(account)
      } yield Ok(views.html.accountHub(account, exposedIamKeys, credReport, flaggedSgs))
    }
  }

  def iam = authAction.async {
    attempt {
      for {
        accountsAndReports <- IAMClient.getAllCredentialReports(accounts)
      } yield Ok(views.html.iam.iam(accountsAndReports.toMap))

    }
  }

  def iamAccount(accountId: String) = authAction.async {
    attempt {
      for {
        account <- AWS.lookupAccount(accountId, accounts)
        client = TrustedAdvisor.client(account)
        exposedIamKeysResult <- TrustedAdvisorExposedIAMKeys.getExposedIAMKeys(client)
        exposedIamKeys = exposedIamKeysResult.flaggedResources
        credReport <- IAMClient.getCredentialsReport(account)
      } yield Ok(views.html.iam.iamAccount(account, exposedIamKeys, credReport))
    }
  }

}

