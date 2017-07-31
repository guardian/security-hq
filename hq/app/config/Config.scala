package config

import model.AwsAccount
import play.api.Configuration

import scala.collection.JavaConverters._


object Config {
  def getAwsAccounts(config: Configuration): List[AwsAccount] = {
    val accounts = for {
      accountConfigs <- config.getConfigList("hq.accounts").toList
      accountConfig <- accountConfigs.asScala
      awsAccount <- getAwsAccount(accountConfig)
    } yield awsAccount
    accounts.sortBy(_.name)
  }

  private[config] def getAwsAccount(config: Configuration): Option[AwsAccount] = {
    for {
      id <- config.getString("id")
      name <- config.getString("name")
      roleArn <- config.getString("roleArn")
    } yield AwsAccount(id, name, roleArn)
  }
}
