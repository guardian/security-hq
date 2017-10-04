package controllers

import java.util.concurrent.Executors

import aws.AWS
import aws.ec2.EC2
import config.Config
import play.api._
import play.api.mvc._
import utils.attempt.PlayIntegration.attempt

import scala.concurrent.ExecutionContext


class SecurityGroupsController(val config: Configuration)(implicit val ec: ExecutionContext) extends Controller {
  private val accounts = Config.getAwsAccounts(config)

  // highly parallel for making simultaneous requests across all AWS accounts
  val highlyAsyncExecutionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  def securityGroups = Action.async {
    attempt {
      for {
        allFlaggedSgs <- EC2.allFlaggedSgs(accounts)(highlyAsyncExecutionContext)
        sortedFlaggedSgs = EC2.sortAccountByFlaggedSgs(allFlaggedSgs)
      } yield Ok(views.html.sgs.sgs(sortedFlaggedSgs))
    }
  }

  def securityGroupsAccount(accountId: String) = Action.async {
    attempt {
      for {
        account <- AWS.lookupAccount(accountId, accounts)
        flaggedSgs <- EC2.flaggedSgsForAccount(account)
      } yield Ok(views.html.sgs.sgsAccount(account, flaggedSgs))
    }
  }

  def dependencies = Action {
    Ok(views.html.dependencies.dependencies())
  }
}
