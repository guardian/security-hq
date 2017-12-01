package config

import java.io.FileInputStream

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.gu.googleauth.{GoogleAuthConfig, GoogleGroupChecker, GoogleServiceAccount}
import model.{AwsAccount, DEV, PROD, Stage}
import play.api.Configuration

import scala.collection.JavaConverters._
import scala.util.Try


object Config {
  def getStage(config: Configuration): Stage = {
    config.getAndValidate("stage", Set("DEV", "PROD")) match {
      case "DEV" => DEV
      case "PROD" => PROD
      case _ => throw config.reportError("stage", s"Missing application stage, expected one of DEV, PROD")
    }
  }

  def googleSettings(implicit config: Configuration): GoogleAuthConfig = {
    val clientId = requiredString(config, "auth.google.clientId")
    val clientSecret = requiredString(config, "auth.google.clientSecret")
    val domain = requiredString(config, "auth.domain")
    val redirectUrl = s"${requiredString(config, "host")}/oauthCallback"
    GoogleAuthConfig(
      clientId,
      clientSecret,
      redirectUrl,
      domain
    )
  }

  def googleGroupChecker(implicit config: Configuration): GoogleGroupChecker = {
    val twoFAUser = requiredString(config, "auth.google.2faUser")
    val serviceAccountCertPath = requiredString(config, "auth.google.serviceAccountCertPath")

    val credentials: GoogleCredential = {
      val jsonCertStream =
        Try(new FileInputStream(serviceAccountCertPath))
          .getOrElse(throw new RuntimeException(s"Could not load service account JSON from $serviceAccountCertPath"))
      GoogleCredential.fromStream(jsonCertStream)
    }

    val serviceAccount = GoogleServiceAccount(
      credentials.getServiceAccountId,
      credentials.getServiceAccountPrivateKey,
      twoFAUser
    )
    new GoogleGroupChecker(serviceAccount)
  }

  def twoFAGroup(implicit config: Configuration): String = {
    requiredString(config, "auth.google.2faGroupId")
  }

  private def requiredString(config: Configuration, key: String): String = {
    config.getOptional[String](key).getOrElse {
      throw new RuntimeException(s"Missing required config property $key")
    }
  }

  def getAwsAccounts(config: Configuration): List[AwsAccount] = {
    val accounts : List[AwsAccount] = for { //underlying.getConfigList(path)).map { configs => configs.asScala.map(Configuration(_))
      accountConfig <- config.underlying.getConfigList("hq.accounts").asScala.map(Configuration(_)).toList
      //accountConfig <- accountConfigs
      awsAccount <- getAwsAccount(accountConfig)
    } yield awsAccount
    accounts.sortBy(_.name)
  }

  private[config] def getAwsAccount(config: Configuration): Option[AwsAccount] = {
    for {
      id <- config.getOptional[String]("id")
      name <- config.getOptional[String]("name")
      roleArn <- config.getOptional[String]("roleArn")
    } yield AwsAccount(id, name, roleArn)
  }
}
