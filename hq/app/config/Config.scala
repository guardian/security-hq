package config

import java.io.FileInputStream

import com.amazonaws.regions.Regions
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.{GoogleCredentials, ServiceAccountCredentials}
import com.gu.googleauth.{AntiForgeryChecker, GoogleAuthConfig, GoogleGroupChecker, GoogleServiceAccount}
import model._
import play.api.Configuration
import play.api.http.HttpConfiguration

import scala.collection.JavaConverters._
import scala.util.Try


object Config {
  val iamHumanUserRotationCadence: Long = 90
  val iamMachineUserRotationCadence: Long = 365
  val iamAlertCadence: Int = 21

  // TODO fetch the region dynamically from the instance
  val region: Regions = Regions.EU_WEST_1
  val documentationLinks = List (
    Documentation("SSH", "Use SSM-Scala for SSH access.", "code", "ssh-access"),
    Documentation("Software dependency checks", "Integrate Snyk for software vulnerability reports.", "security", "snyk"),
    Documentation("Wazuh", "Guide to installing the Wazuh agent.", "scanner", "wazuh"),
    Documentation("Vulnerabilities", "Developer guiide to addressing vulnerabilities.", "format_list_numbered", "vulnerability-management")
  )

  def getStage(config: Configuration): Stage = {
    config.getAndValidate("stage", Set("DEV", "PROD")) match {
      case "DEV" => DEV
      case "PROD" => PROD
      case _ => throw config.reportError("stage", s"Missing application stage, expected one of DEV, PROD")
    }
  }

  def googleSettings(httpConfiguration: HttpConfiguration, config: Configuration): GoogleAuthConfig = {
    val clientId = requiredString(config, "auth.google.clientId")
    val clientSecret = requiredString(config, "auth.google.clientSecret")
    val domain = requiredString(config, "auth.domain")
    val redirectUrl = s"${requiredString(config, "host")}/oauthCallback"
    GoogleAuthConfig(
      clientId,
      clientSecret,
      redirectUrl,
      domain,
      antiForgeryChecker = AntiForgeryChecker.borrowSettingsFromPlay(httpConfiguration)
    )
  }

  def gcpSccAuthentication(implicit config: Configuration): GcpSccConfig = {
    val gcpOrgId = requiredString(config, "gcp.orgId")
    val gcpSccSourceId = requiredString(config, "gcp.sccSourceId")
    GcpSccConfig(gcpOrgId, gcpSccSourceId)
  }

  def gcpCredentialsProvider(implicit config: Configuration): FixedCredentialsProvider = {
    val serviceAccountCertPath = requiredString(config, "auth.google.serviceAccountCertPath")
    val tmpCredsFile = new FileInputStream(serviceAccountCertPath)
    val scopesTmp= "https://www.googleapis.com/auth/cloud-platform"
    val googleCredential = GoogleCredentials.fromStream(tmpCredsFile).createScoped(scopesTmp)
    FixedCredentialsProvider.create(googleCredential)
  }

  def googleGroupChecker(implicit config: Configuration): GoogleGroupChecker = {
    val twoFAUser = requiredString(config, "auth.google.2faUser")
    val serviceAccountCertPath = requiredString(config, "auth.google.serviceAccountCertPath")
    val credentials: ServiceAccountCredentials = {
      val jsonCertStream =
        Try(new FileInputStream(serviceAccountCertPath))
          .getOrElse(throw new RuntimeException(s"Could not load service account JSON from $serviceAccountCertPath"))
      ServiceAccountCredentials.fromStream(jsonCertStream)
    }

    val serviceAccount = GoogleServiceAccount(
      credentials.getClientEmail,
      credentials.getPrivateKey,
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
      number <- config.getOptional[String]("number")
    } yield AwsAccount(id, name, roleArn, number)
  }

  def getSnykSSOUrl(config: Configuration): Option[String] = {
    config.getOptional[String]("snykSSOUrl")
  }

  def getAnghammaradSNSTopicArn(config: Configuration): Option[String] = config.getOptional[String]("anghammaradSnsArn")
  def getIamDynamoTableName(config: Configuration): Option[String] = config.getOptional[String]("iamDynamoTableName")
}
