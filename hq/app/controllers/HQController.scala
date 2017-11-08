package controllers

import auth.SecurityHQAuthActions
import aws.AWS
import aws.support.{TrustedAdvisor, TrustedAdvisorExposedIAMKeys}
import config.Config
import play.api._
import play.api.libs.ws.WSClient
import play.api.mvc._
import utils.attempt.PlayIntegration.attempt

import scala.concurrent.ExecutionContext


class HQController(val config: Configuration)
                  (implicit val ec: ExecutionContext, val wsClient: WSClient)
  extends Controller  with SecurityHQAuthActions {

  private val accounts = Config.getAwsAccounts(config)

  def index = AuthAction {
    Ok(views.html.index(accounts))
  }

  def iam = AuthAction {
    Ok(views.html.iam.iam())
  }

  def iamAccount(accountId: String) = AuthAction.async {
    attempt {
      for {
        account <- AWS.lookupAccount(accountId, accounts)
        client = TrustedAdvisor.client(account)
        exposedIamKeysResult <- TrustedAdvisorExposedIAMKeys.getExposedIAMKeys(client)
        exposedIamKeys = exposedIamKeysResult.flaggedResources
      } yield Ok(views.html.iam.iamAccount(account, exposedIamKeys))
    }
  }


}
