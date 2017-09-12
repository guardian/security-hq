package controllers

import config.Config
import play.api._
import play.api.mvc._


class HQController(val config: Configuration) extends Controller {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  private val accounts = Config.getAwsAccounts(config)
  Logger.info("AWS accounts: " + accounts.map(_.name).mkString(", "))

  def index = Action {
    Ok(views.html.index(accounts))
  }

  def account(accountId: String) = Action {
    accounts.find(_.id == accountId).fold(NotFound: Result) { account =>
      Ok(views.html.account(account))
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

  def securityGroups = Action {
    Ok(views.html.sgs.sgs())
  }

  def securityGroupsAccount(accountId: String) = Action {
    accounts.find(_.id == accountId).fold(NotFound: Result) { account =>
      Ok(views.html.sgs.sgsAccount(account))
    }
  }

  def dependencies = Action {
    Ok(views.html.dependencies.dependencies())
  }

  def dependenciesAccount(accountId: String) = Action {
    accounts.find(_.id == accountId).fold(NotFound: Result) { account =>
      Ok(views.html.dependencies.dependenciesAccount(account))
    }
  }
}
