package controllers

import auth.SecurityHQAuthActions
import aws.AWS
import config.Config
import play.api._
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.CacheService
import utils.attempt.PlayIntegration.attempt
import logic.ReportDisplay.sortAccountsByReportSummary


import scala.concurrent.ExecutionContext

class HQController (val config: Configuration, cacheService: CacheService)
                   (implicit val ec: ExecutionContext, val wsClient: WSClient, val bodyParser: BodyParser[AnyContent], val controllerComponents: ControllerComponents, val assetsFinder: AssetsFinder)
  extends BaseController  with SecurityHQAuthActions {

  private val accounts = Config.getAwsAccounts(config)

  def index = authAction {
    Ok(views.html.index(accounts))
  }

  def iam = authAction {
    val accountsAndReports = cacheService.getAllCredentials()
    val sortedAccountsAndReports = sortAccountsByReportSummary(accountsAndReports.toList)
    Ok(views.html.iam.iam(sortedAccountsAndReports))
  }

  def iamAccount(accountId: String) = authAction.async {
    attempt {
      for {
        account <- AWS.lookupAccount(accountId, accounts)
        exposedIamKeys = cacheService.getExposedKeysForAccount(account)
        credReport = cacheService.getCredentialsForAccount(account)
      } yield Ok(views.html.iam.iamAccount(account, exposedIamKeys, credReport))
    }
  }

}

