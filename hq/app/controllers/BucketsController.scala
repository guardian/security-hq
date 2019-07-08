package controllers

import auth.SecurityHQAuthActions
import aws.AWS
import com.gu.googleauth.GoogleAuthConfig
import config.Config
import logic.PublicBucketsDisplay
import play.api._
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.CacheService
import utils.attempt.PlayIntegration.attempt

import scala.concurrent.ExecutionContext

class BucketsController(val config: Configuration, cacheService: CacheService, val authConfig: GoogleAuthConfig)
                           (implicit val ec: ExecutionContext, val wsClient: WSClient, val bodyParser: BodyParser[AnyContent], val controllerComponents: ControllerComponents, val assetsFinder: AssetsFinder)
  extends BaseController  with SecurityHQAuthActions {

  private val accounts = Config.getAwsAccounts(config)

  def buckets: Action[AnyContent] = authAction {
    val viewData = PublicBucketsDisplay.accountsBucketData(cacheService.getAllPublicBuckets.toList)
    Ok(views.html.s3.publicBuckets(viewData))
  }

  def bucketsAccount(accountId: String): Action[AnyContent] = authAction.async {
    attempt {
      for {
        account <- AWS.lookupAccount(accountId, accounts)
        (_, publicBuckets) = PublicBucketsDisplay.accountBucketData((account, cacheService.getPublicBucketsForAccount(account)))
      } yield Ok(views.html.s3.publicBucketsAccount(account, publicBuckets))
    }
  }
}
