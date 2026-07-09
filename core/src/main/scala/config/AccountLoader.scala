package config

import com.typesafe.config.{Config, ConfigFactory}
import model.AwsAccount

import scala.jdk.CollectionConverters.*
import scala.util.Try

/** Loads AWS accounts. */
object AccountLoader {

  def getAwsAccounts(config: Config): List[AwsAccount] = {
    val accounts = for {
      accountConfig <- config.getConfigList("AWS_ACCOUNTS").asScala.toList
      account <- getAwsAccount(accountConfig)
    } yield account
    accounts.sortBy(_.name)
  }

  /** Parses `AWS_ACCOUNTS` list from raw Hocon text. */
  def getAwsAccountsFromString(hocon: String): List[AwsAccount] =
    getAwsAccounts(ConfigFactory.parseString(hocon))

  private def getAwsAccount(config: Config): Option[AwsAccount] =
    for {
      id <- optString(config, "id")
      name <- optString(config, "name")
      roleArn <- optString(config, "roleArn")
      number <- optString(config, "number")
    } yield AwsAccount(id, name, roleArn, number)

  private def optString(config: Config, key: String): Option[String] =
    Try(config.getString(key)).toOption
}
