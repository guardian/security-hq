package config

import java.io.FileInputStream
import com.amazonaws.regions.Regions
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.{GoogleCredentials, ServiceAccountCredentials}
import com.gu.googleauth.{AntiForgeryChecker, GoogleAuthConfig, GoogleGroupChecker, GoogleServiceAccount}
import model._
import org.apache.commons.lang3.exception.ExceptionContext
import play.api.Configuration
import play.api.http.HttpConfiguration
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.Try


object Config {
  val iamHumanUserRotationCadence: Long = 90
  val iamMachineUserRotationCadence: Long = 365
  val iamAlertCadence: Int = 21
  val daysBetweenWarningAndFinalNotification = 21
  val daysBetweenFinalNotificationAndRemediation = 7

  // TODO fetch the region dynamically from the instance
  val region: Regions = Regions.EU_WEST_1
  val documentationLinks = List (
    Documentation("SSH", "Use SSM-Scala for SSH access.", "code", "ssh-access"),
    Documentation("Software dependency checks", "Integrate Snyk for software vulnerability reports.", "security", "snyk"),
    Documentation("Wazuh", "Guide to installing the Wazuh agent.", "scanner", "wazuh"),
    Documentation("Vulnerabilities", "Developer guide to addressing vulnerabilities.", "format_list_numbered", "vulnerability-management")
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

  def getSnykConfig(config: Configuration): SnykConfig = {
    SnykConfig(
      SnykToken(requiredString(config, "snyk.token")),
      SnykGroupId(requiredString(config, "snyk.organisation"))
    )
  }

  def getSnykSSOUrl(config: Configuration): Option[String] = {
    config.getOptional[String]("snyk.ssoUrl")
  }

  def getIamUnrecognisedUserConfig(config: Configuration)(implicit ec: ExecutionContext): Attempt[UnrecognisedJobConfigProperties] = {
    for {
      accounts <- getAllowedAccountsForStage(config)
      key <- getJanusDataFileKey(config)
      bucket <- getIamUnrecognisedUserBucket(config)
      region <- getIamUnrecognisedUserBucketRegion(config)
      securityAccount <- getSecurityAccount(config)
      anghammaradSnsTopic <- getAnghammaradSnsTopic(config)
    } yield UnrecognisedJobConfigProperties(accounts, key, bucket, Regions.fromName(region), securityAccount, anghammaradSnsTopic)
  }


  def getAllowedAccountsForStage(config: Configuration): Attempt[List[String]] = {
    Attempt.fromOption(
      config.getOptional[Seq[String]]("alert.allowedAccountIds").map(_.toList),
      FailedAttempt(Failure("unable to get list of accounts allowed to make changes to AWS. Rectify this by adding allowed accounts to config.",
        "I haven't been able to get a list of allowed AWS accounts, which should be in Security HQ's config. Check ~/.gu/security-hq.local.conf or for PROD, check S3 for security-hq.conf.",
        500
      ))
    )
  }

  def getJanusDataFileKey(config: Configuration): Attempt[String] = {
    Attempt.fromOption(
      config.getOptional[String]("alert.iamUnrecognisedUserS3Key"),
      FailedAttempt(Failure("unable to get janus data file key from config for the IAM unrecognised job",
        "I haven't been able to get the Janus S3 file key from config. Please check ~/.gu/security-hq.local.conf for local conf or security-hq.conf in S3 for PROD conf.",
        500)
      )
    )
  }

  def getIamUnrecognisedUserBucket(config: Configuration): Attempt[String] = {
    Attempt.fromOption(
      config.getOptional[String]("alert.iamUnrecognisedUserS3Bucket"),
      FailedAttempt(Failure("unable to get IAM unrecognised user bucket from config",
        "I haven't been able to get the S3 bucket, which contains the janus data used for the unrecognised user job. Please check ~/.gu/security-hq.local.conf for local conf or security-hq.conf in S3 for PROD conf.",
        500)
      )
    )
  }

  def getIamUnrecognisedUserBucketRegion(config: Configuration): Attempt[String] = {
    Attempt.fromOption(
      config.getOptional[String]("alert.iamUnrecognisedUserS3BucketRegion"),
      FailedAttempt(Failure("unable to get IAM unrecognised user bucket region from config",
        "I haven't been able to get the S3 bucket region for the unrecognised user job. Please check ~/.gu/security-hq.local.conf for local conf or security-hq.conf in S3 for PROD conf.",
        500)
      )
    )
  }

  def getSecurityAccount(config: Configuration): Attempt[AwsAccount] = {
    Attempt.fromOption(
      Config.getAwsAccounts(config).find(_.id == "security"),
      FailedAttempt(Failure("unable to find security account details from config",
        "I haven't been able to get the security account details from config",
        500))
    )
  }

  def getAnghammaradSnsTopic(config: Configuration): Attempt[String] = {
    Attempt.fromOption(
      config.getOptional[String]("alert.anghammaradSnsArn"),
      FailedAttempt(Failure("unable to retrieve Anghammarad SNS Topic from config",
        "I haven't been able to get the Anghammarad SNS Topic from config",
        500))
    )
  }

  def getIamDynamoTableName(config: Configuration): Attempt[String] = {
    Attempt.fromOption(
      config.getOptional[String]("alert.iamDynamoTableName"),
      FailedAttempt(Failure("unable to retrieve DynamoDB table name from config",
        "I haven't been able to get the DynamoDB table name from config for the IAM vulnerable user job.",
        500))
    )
  }

}
