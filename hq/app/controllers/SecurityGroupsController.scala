package controllers

import auth.SecurityHQAuthActions
import aws.AWS
import aws.ec2.EC2
import com.gu.googleauth.GoogleAuthConfig
import config.Config
import play.api._
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.CacheService
import utils.attempt.PlayIntegration.attempt

import scala.concurrent.ExecutionContext


class SecurityGroupsController(val config: Configuration, cacheService: CacheService, val authConfig: GoogleAuthConfig)
                              (implicit val ec: ExecutionContext, val wsClient: WSClient, val bodyParser: BodyParser[AnyContent], val controllerComponents: ControllerComponents, val assetsFinder: AssetsFinder)
  extends BaseController with SecurityHQAuthActions {

  private val accounts = Config.getAwsAccounts(config)

  def securityGroups = authAction {
    val allFlaggedSgs = cacheService.getAllSgs
    val sortedFlaggedSgs = EC2.sortAccountByFlaggedSgs(allFlaggedSgs.toList)
    Ok(views.html.sgs.sgs(sortedFlaggedSgs))
  }

  def securityGroupsAccount(accountId: String): Action[AnyContent] = authAction.async {
    attempt {
      for {
        account <- AWS.lookupAccount(accountId, accounts)
        flaggedSgs = cacheService.getSgsForAccount(account)
      } yield Ok(views.html.sgs.sgsAccount(account, flaggedSgs))
    }
  }

}
