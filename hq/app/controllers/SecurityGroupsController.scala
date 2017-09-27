package controllers

import aws.AWS
import aws.ec2.EC2
import aws.support.{TrustedAdvisor, TrustedAdvisorSGOpenPorts}
import config.Config
import play.api._
import play.api.mvc._
import utils.attempt.PlayIntegration.attempt

import scala.concurrent.ExecutionContext


class SecurityGroupsController(val config: Configuration)(implicit val ec: ExecutionContext) extends Controller {
  private val accounts = Config.getAwsAccounts(config)

  def securityGroups = Action {
    Ok(views.html.sgs.sgs())
  }

  def securityGroupsAccount(accountId: String) = Action.async {
    attempt {
      for {
        account <- AWS.lookupAccount(accountId, accounts)
        supportClient = TrustedAdvisor.client(account)
        sgResult <- TrustedAdvisorSGOpenPorts.getSGOpenPorts(supportClient)
        sgUsage <- EC2.getSgsUsage(sgResult, account)
      } yield Ok(views.html.sgs.sgsAccount(account, sgUsage, sgResult))
    }
  }

  def dependencies = Action {
    Ok(views.html.dependencies.dependencies())
  }
}
