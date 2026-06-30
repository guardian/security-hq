package config

import com.typesafe.config.{Config, ConfigFactory}
import model.AwsAccount

import scala.jdk.CollectionConverters.*
import scala.util.Try

/** Play-free loader for the list of AWS accounts.
  *
  * Parses the same `hq.accounts` block used by the Security HQ Play app (see
  * `config.Config.getAwsAccounts`) directly from a Typesafe `Config`, so it can
  * be reused outside of a Play application (e.g. from the cloudwatch-metrics
  * Lambda).
  */
object AccountLoader {

  /** Parse the `hq.accounts` list from the supplied Typesafe config, sorted by
    * account name.
    */
  def getAwsAccounts(config: Config): List[AwsAccount] = {
    val accounts = for {
      accountConfig <- config.getConfigList("hq.accounts").asScala.toList
      account <- getAwsAccount(accountConfig)
    } yield account
    accounts.sortBy(_.name)
  }

  /** Parse the `hq.accounts` list from raw HOCON text (e.g. the contents of
    * `security-hq.conf` fetched from S3).
    */
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
