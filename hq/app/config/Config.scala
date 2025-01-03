package config


import com.gu.play.secretrotation.aws.parameterstore.SecretSupplier
import com.gu.play.secretrotation.aws.parameterstore.AwsSdkV2


import aws.AwsClient
import com.google.auth.oauth2.{ServiceAccountCredentials}
import com.gu.googleauth.{AntiForgeryChecker, GoogleAuthConfig, GoogleGroupChecker}


import com.gu.play.secretrotation.{RotatingSecretComponents, SnapshotProvider, TransitionTiming}
import model._
import play.api.Configuration
import play.api.http.HttpConfiguration
import utils.attempt.{Attempt, FailedAttempt, Failure}

import java.io.FileInputStream
import java.time.Duration.{ofHours, ofMinutes}
import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext
import scala.util.Try

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssm.SsmClient



object Config {
  val iamHumanUserRotationCadence: Long = 90
  val iamMachineUserRotationCadence: Long = 365
  val outdatedCredentialOptOutUserTag = "SecurityHQ::OutdatedCredentialOptOut"
  val daysBetweenWarningAndFinalNotification = 7
  val daysBetweenFinalNotificationAndRemediation = 7
  val app = "security-hq"

  // TODO fetch the region dynamically from the instance
  val region: Region = Region.of("eu-west-1")
  val documentationLinks: List[Documentation] = List (
    Documentation("SSH", "Use SSM-Scala for SSH access.", "code", "ssh-access"),
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

  def googleSettings(stage: Stage, stack: String, config: Configuration, ssmClient: SsmClient): GoogleAuthConfig = {
    val clientId = requiredString(config, "auth.google.clientId")
    val clientSecret = requiredString(config, "auth.google.clientSecret")
    val domain = requiredString(config, "auth.domain")
    val redirectUrl = s"${requiredString(config, "host")}/oauthCallback"

    val secretStateSupplier: SnapshotProvider = {
      new SecretSupplier(
        TransitionTiming(usageDelay = ofMinutes(3), overlapDuration = ofHours(2)),
        s"/${stage.toString}/$stack/$app/play.http.secret.key",
        AwsSdkV2(ssmClient)
      )
    }

    GoogleAuthConfig(
      clientId,
      clientSecret,
      redirectUrl,
      List(domain),
      antiForgeryChecker = AntiForgeryChecker(secretStateSupplier)
    )
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

    new GoogleGroupChecker(twoFAUser, credentials)
  }

  def twoFAGroup(implicit config: Configuration): String = {
    requiredString(config, "auth.google.2faGroupId")
  }
  def departmentGroup(implicit config: Configuration): String = {
    requiredString(config, "auth.google.departmentGroupId")
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


  def getIamDynamoTableName(config: Configuration): Attempt[String] = {
    Attempt.fromOption(
      config.getOptional[String]("alert.iamDynamoTableName"),
      FailedAttempt(Failure("unable to get dynamo table name",
        "unable to get dynamo table name for IAM jobs",
        500
      ))
    )
  }

  def getIamUnrecognisedUserConfig(config: Configuration)(implicit ec: ExecutionContext): Attempt[UnrecognisedJobConfigProperties] = {
    for {
      accounts <- getAllowedAccountsForStage(config)
      key <- getJanusDataFileKey(config)
      bucket <- getIamUnrecognisedUserBucket(config)
      securityAccount <- getSecurityAccount(config)
      anghammaradSnsTopicArn <- getAnghammaradSNSTopicArn(config)
    } yield UnrecognisedJobConfigProperties(accounts, key, bucket, securityAccount, anghammaradSnsTopicArn)
  }

  def getAnghammaradSNSTopicArn(config: Configuration): Attempt[String] = {
    Attempt.fromOption(
      config.getOptional[String]("alert.anghammaradSnsArn"),
      FailedAttempt(Failure("unable to get Anghammarad topic ARN",
        "unable to get Anghammarad topic ARN for IAM jobs",
        500
      ))
    )
  }

  def getAccountsForIamRemediationService(config: Configuration): Attempt[List[String]] = {
    Attempt.fromOption(
      config.getOptional[Seq[String]]("alert.accountIdsForIamRemediationService").map(_.toList),
      FailedAttempt(Failure("unable to get list of accounts to run the IAM Remediation Service on. Rectify this by adding account ids to config.",
        "Add account Ids for Iam Remediation service to ~/.gu/security-hq.local.conf or for PROD, check S3 for security-hq.conf.",
        500
      ))
    )
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
}
