package controllers

import auth.SecurityHQAuthActions
import aws.AWS
import aws.ec2.EC2
import config.Config
import play.api._
import play.api.libs.ws.WSClient
import play.api.mvc._
import service.SecurityGroups
import utils.attempt.PlayIntegration.attempt

import scala.concurrent.ExecutionContext


class SecurityGroupsController(val config: Configuration)
                              (implicit val ec: ExecutionContext, val wsClient: WSClient, val bodyParser: BodyParser[AnyContent], val controllerComponents: ControllerComponents, val assetsFinder: AssetsFinder)
  extends BaseController with SecurityHQAuthActions {

  private val accounts = Config.getAwsAccounts(config)

  def securityGroups = authAction.async {
    attempt {
      for {
        allFlaggedSgs <- SecurityGroups.getFlaggedSecurityGroups
        sortedFlaggedSgs = EC2.sortAccountByFlaggedSgs(allFlaggedSgs)
      } yield Ok(views.html.sgs.sgs(sortedFlaggedSgs))
    }
  }

  def securityGroupsAccount(accountId: String) = authAction.async {
    attempt {
      for {
        account <- AWS.lookupAccount(accountId, accounts)
        flaggedSgs <- EC2.flaggedSgsForAccount(account)
      } yield Ok(views.html.sgs.sgsAccount(account, flaggedSgs))
    }
  }


}
