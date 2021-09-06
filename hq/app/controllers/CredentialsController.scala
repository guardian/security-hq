package controllers

import auth.SecurityHQAuthActions
import aws.AWS
import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.models.{Notification, AwsAccount => Account}
import com.gu.googleauth.GoogleAuthConfig
import config.Config
import logic.CredentialsReportDisplay.{exposedKeysSummary, sortAccountsByReportSummary}
import model.{AccessKey, AccessKeyDisabled, AccessKeyEnabled, AwsAccount, NoKey, VulnerableUser}
import org.joda.time.DateTime
import play.api._
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc._
import schedule.IamMessages.{createWarningMessage, warningSubject}
import schedule.IamNotifier.notification
import schedule.{Dynamo, IamJob, IamNotifier, IamTestNotifications}
import services.CacheService
import utils.attempt.PlayIntegration.attempt

import scala.concurrent.ExecutionContext

class CredentialsController(val config: Configuration, cacheService: CacheService, val authConfig: GoogleAuthConfig, val iamJob: IamJob, val configuration: Configuration, val dynamo: Dynamo, val snsClient: AmazonSNSAsync)
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

  def testWarningNotification() = authAction {
    IamTestNotifications.sendTestNotification(snsClient, iamJob.topicArn, IamTestNotifications.Warning)
    Ok("Triggered warning notification emails")
  }

  def testFinalNotification() = authAction {
    IamTestNotifications.sendTestNotification(snsClient, iamJob.topicArn, IamTestNotifications.Final)
    Ok("Triggered final notification emails")
  }

  def testDisabledUserNotification() = authAction {
    IamTestNotifications.sendTestNotification(snsClient, iamJob.topicArn, IamTestNotifications.Disabled)
    Ok("Triggered disabled user notification emails")
  }

  def testNotifications() = authAction {
    iamJob.run(testMode = true)
    Ok("Triggered notifications job")
  }

  def getNotifications() = authAction {
    val result = dynamo.scanAlert()
    Ok(Json.toJson(result))
  }
}
