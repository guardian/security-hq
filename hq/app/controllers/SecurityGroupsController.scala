package controllers

import aws.ec2.EC2
import aws.support.{TrustedAdvisor, TrustedAdvisorSGOpenPorts}
import config.Config
import play.api._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}


class SecurityGroupsController(val config: Configuration)(implicit val ec: ExecutionContext) extends Controller {

  /**
    * Create an Action to render an HTML page with a welcome message.
    * The configuration in the `routes` file means that this method
    * will be called when the application receives a `GET` request with
    * a path of `/`.
    */
  private val accounts = Config.getAwsAccounts(config)

  def securityGroups = Action {
    Ok(views.html.sgs.sgs())
  }

  def securityGroupsAccount(accountId: String) = Action.async {
    accounts.find(_.id == accountId).fold(Future.successful(NotFound: Result)) { account =>
      val supportClient = TrustedAdvisor.client(account)
      for {
        sgResult <- TrustedAdvisorSGOpenPorts.getSGOpenPorts(supportClient)
        sgUsage <- EC2.getSgsUsage(sgResult, account)
      } yield Ok(views.html.sgs.sgsAccount(account, sgUsage, sgResult))
    }
  }

  def securityGroupInfo(accountId: String, sgId: String) = Action.async { request =>
    accounts.find(_.id == accountId).fold(Future.successful(NotFound: Result)) { account =>
      val client = ???
      ???
    }
  }

  def dependencies = Action {
    Ok(views.html.dependencies.dependencies())
  }
}
