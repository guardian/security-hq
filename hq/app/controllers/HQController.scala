package controllers

import aws.support.{TrustedAdvisor, TrustedAdvisorSGOpenPorts}
import config.Config
import play.api._
import play.api.mvc._
import aws.Auth

import scala.concurrent.{ExecutionContext, Future}


class HQController(val config: Configuration)(implicit val ec: ExecutionContext) extends Controller {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  private val accounts = Config.getAwsAccounts(config)

  def index = Action {
    Ok(views.html.index(accounts))
  }

  def account(accountId: String) = Action.async {
    accounts.find(_.id == accountId).fold(Future.successful(NotFound: Result)) { account =>
      val client = TrustedAdvisor.client(account)
      for {
        trustedAdvisorDescs <- TrustedAdvisor.getTrustedAdvisorChecks(client)
      } yield Ok(views.html.account(account, trustedAdvisorDescs))
    }
  }

  def iam = Action {
    Ok(views.html.iam.iam())
  }

  def iamAccount(accountId: String) = Action {
    accounts.find(_.id == accountId).fold(NotFound: Result) { account =>
      Ok(views.html.iam.iamAccount(account))
    }
  }

  def dependencies = Action {
    Ok(views.html.dependencies.dependencies())
  }
}
