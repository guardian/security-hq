package controllers

import aws.AWS
import aws.support.{TrustedAdvisor, TrustedAdvisorExposedIAMKeys}
import config.Config
import model.ExposedIAMKeyDetail
import play.api._
import play.api.mvc._
import utils.attempt.PlayIntegration.attempt

import scala.concurrent.ExecutionContext


class HQController(val config: Configuration)(implicit val ec: ExecutionContext) extends Controller {
  private val accounts = Config.getAwsAccounts(config)

  def index = Action {
    Ok(views.html.index(accounts))
  }

  def account(accountId: String) = Action.async {
    attempt {
      for {
        account <- AWS.lookupAccount(accountId, accounts)
        client = TrustedAdvisor.client(account)
        trustedAdvisorDescs <- TrustedAdvisor.getTrustedAdvisorChecks(client)
      } yield Ok(views.html.account(account, trustedAdvisorDescs))
    }
  }

  def iam = Action {
    Ok(views.html.iam.iam())
  }

  def iamAccount(accountId: String) = Action.async {
    attempt {
      for {
        account <- AWS.lookupAccount(accountId, accounts)
        client = TrustedAdvisor.client(account)
        exposedIamKeysResult <- TrustedAdvisorExposedIAMKeys.getExposedIAMKeys(client)
        //exposedIamKeys = exposedIamKeysResult.flaggedResources
        exposedIamKeys = List(
          ExposedIAMKeyDetail("key-id", "afisher", "Bad fraud", "2136357781", "2017-29-09T11:32:04Z", "The internet", "Soon", "EC2")
        )
      } yield Ok(views.html.iam.iamAccount(account, exposedIamKeys))
    }
  }

  def dependencies = Action {
    Ok(views.html.dependencies.dependencies())
  }
}
