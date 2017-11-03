package controllers

import java.util.concurrent.Executors

import auth.SecurityHQAuthActions
import aws.AWS
import aws.ec2.EC2
import config.Config
import play.api._
import play.api.libs.ws.WSClient
import play.api.mvc._
import utils.attempt.PlayIntegration.attempt

import scala.concurrent.ExecutionContext


class SecurityGroupsController(val config: Configuration)
                              (implicit val ec: ExecutionContext, val wsClient: WSClient)
  extends Controller with SecurityHQAuthActions {

  private val accounts = Config.getAwsAccounts(config)

  // highly parallel for making simultaneous requests across all AWS accounts
  val highlyAsyncExecutionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  def securityGroups = AuthAction.async {
    attempt {
      for {
        allFlaggedSgs <- EC2.allFlaggedSgs(accounts)(highlyAsyncExecutionContext)
        sortedFlaggedSgs = EC2.sortAccountByFlaggedSgs(allFlaggedSgs)
      } yield Ok(views.html.sgs.sgs(sortedFlaggedSgs))
    }
  }

  def securityGroupsAccount(accountId: String) = AuthAction.async {
    attempt {
      for {
        account <- AWS.lookupAccount(accountId, accounts)
        flaggedSgs <- EC2.flaggedSgsForAccount(account)
      } yield Ok(views.html.sgs.sgsAccount(account, flaggedSgs))
    }
  }

  def dependencies = AuthAction {
    Ok(views.html.dependencies.dependencies())
  }
}
