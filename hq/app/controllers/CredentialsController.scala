package controllers

import auth.SecurityHQAuthActions
import aws.AWS
import com.gu.googleauth.GoogleAuthConfig
import config.Config
import logic.CredentialsReportDisplay.{exposedKeysSummary, sortAccountsByReportSummary}
import play.api._
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.CacheService
import utils.attempt.PlayIntegration.attempt

import scala.concurrent.ExecutionContext

class CredentialsController(val config: Configuration, cacheService: CacheService, val authConfig: GoogleAuthConfig)
                           (implicit val ec: ExecutionContext, val wsClient: WSClient, val bodyParser: BodyParser[AnyContent], val controllerComponents: ControllerComponents, val assetsFinder: AssetsFinder)
  extends BaseController  with SecurityHQAuthActions {

  private val accounts = Config.getAwsAccounts(config)

  def iam = authAction {
    val exposedKeys = cacheService.getAllExposedKeys
    val keysSummary = exposedKeysSummary(exposedKeys)
    val accountsAndReports = cacheService.getAllCredentials
    val sortedAccountsAndReports = sortAccountsByReportSummary(accountsAndReports.toList)
    Ok(views.html.iam.iam(sortedAccountsAndReports, keysSummary))
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

  def refresh() = authAction {
    cacheService.refreshCredentialsBox()
    Ok("Refreshing IAM credentials reports (may take a minute or so to appear)")
  }
}
