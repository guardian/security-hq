package config

import com.google.auth.oauth2.ServiceAccountCredentials
import com.gu.googleauth.{AntiForgeryChecker, GoogleAuthConfig, GoogleGroupChecker}
import com.gu.play.secretrotation.aws.parameterstore.{AwsSdkV2, SecretSupplier}
import com.gu.play.secretrotation.{SnapshotProvider, TransitionTiming}
import model.*
import play.api.{Configuration, Logging}
import software.amazon.awssdk.services.ssm.SsmClient
import utils.attempt.{Attempt, FailedAttempt, Failure}

import java.io.FileInputStream
import java.time.Duration.{ofHours, ofMinutes}
import scala.jdk.CollectionConverters.*
import scala.util.Try

object Config extends Logging {
  val app = "security-hq"

  val documentationLinks: List[Documentation] = List(
    Documentation("SSH", "Use SSM-Scala for SSH access.", "code", "ssh-access"),
    Documentation("Wazuh", "Guide to installing the Wazuh agent.", "scanner", "wazuh"),
    Documentation(
      "Vulnerabilities",
      "Developer guide to addressing vulnerabilities.",
      "format_list_numbered",
      "vulnerability-management"
    )
  )

  def getStage(config: Configuration): Stage = {
    config.getAndValidate("stage", Set("DEV", "PROD")) match {
      case "DEV"  => DEV
      case "PROD" => PROD
      case _      => throw config.reportError("stage", s"Missing application stage, expected one of DEV, PROD")
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
    val accounts: List[AwsAccount] =
      for { // underlying.getConfigList(path)).map { configs => configs.asScala.map(Configuration(_))
        accountConfig <- config.underlying.getConfigList("hq.accounts").asScala.map(Configuration(_)).toList
        // accountConfig <- accountConfigs
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

  def getIamDynamoTableName(config: Configuration): String = requiredString(config, "alert.iamDynamoTableName")

  def getAnghammaradSNSTopicArn(config: Configuration): String = requiredString(config, "alert.anghammaradSnsArn")

  // Default to true; only an explicit "false" disables dry-run.
  // Not using toBoolean because we want to default to true (do nothing) if the config is missing or invalid
  private[config] def getDryRun(config: Configuration, key: String) =
    !config.getOptional[String](s"$key.dryRun").exists(_.equalsIgnoreCase("false"))

  def getOutdatedCredentialsDryRun(config: Configuration): Boolean = {
    val b = getDryRun(config, "outdatedCredentials")
    logger.info(s"outdatedCredentials dry run is set to $b")
    b
  }

  def getAccountsForIamRemediationService(config: Configuration): Attempt[List[String]] = {
    Attempt.fromOption(
      config.getOptional[Seq[String]]("alert.accountIdsForIamRemediationService").map(_.toList),
      FailedAttempt(
        Failure(
          "unable to get list of accounts to run the IAM Remediation Service on. Rectify this by adding account ids to config.",
          "Add account Ids for Iam Remediation service to ~/.gu/security-hq/security-hq.local.conf or for PROD, check S3 for security-hq.conf.",
          500
        )
      )
    )
  }

  def getAllowedAccountsForStage(config: Configuration): Attempt[List[String]] = {
    Attempt.fromOption(
      config.getOptional[Seq[String]]("alert.allowedAccountIds").map(_.toList),
      FailedAttempt(
        Failure(
          "unable to get list of accounts allowed to make changes to AWS. Rectify this by adding allowed accounts to config.",
          "I haven't been able to get a list of allowed AWS accounts, which should be in Security HQ's config. Check ~/.gu/security-hq/security-hq.local.conf or for PROD, check S3 for security-hq.conf.",
          500
        )
      )
    )
  }
}
