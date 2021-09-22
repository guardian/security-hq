package schedule.unrecognised

import aws.{AwsClient, AwsClients}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.models.{AwsAccount => TargetAccount}
import com.gu.janus.JanusConfig
import config.Config
import config.Config.getAnghammaradSNSTopicArn
import model.{CredentialReportDisplay, CronSchedule, VulnerableUser, AwsAccount => Account}
import play.api.{Configuration, Logging}
import schedule.IamMessages.FormerStaff.disabledUsersMessage
import schedule.IamMessages.disabledUsersSubject
import schedule.Notifier.{notification, send}
import schedule.unrecognised.IamUnrecognisedUsers.{filterUnrecognisedIamUsers, getCredsReportDisplayForAccount, getJanusUsernames}
import schedule.vulnerable.IamDisableAccessKeys.disableAccessKeys
import schedule.vulnerable.IamRemovePassword.removePasswords
import schedule.{CronSchedules, JobRunner}
import services.CacheService
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.ExecutionContext
import scala.io.BufferedSource

class IamUnrecognisedUserJob(
  cacheService: CacheService,
  snsClient: AmazonSNSAsync,
  s3Clients: AwsClients[AmazonS3],
  iamClients: AwsClients[AmazonIdentityManagementAsync],
  config: Configuration
)(implicit val executionContext: ExecutionContext) extends JobRunner with Logging {
  override val id: String = "unrecognised-iam-users"
  override val description: String = "Check for and remove unrecognised human IAM users"
  override val cronSchedule: CronSchedule = CronSchedules.everyWeekDay
  private val topicArn: Option[String] = getAnghammaradSNSTopicArn(config)
  private val allCredsReports = cacheService.getAllCredentials

  def run(testMode: Boolean): Unit = {
    if (testMode) {
      logger.info(s"Skipping scheduled $id job as it is not enabled")
    } else {
      logger.info(s"Running scheduled job: $description")
    }

    val result = for {
      securityAccount <- getSecurityAccount
      client <- s3Clients.get(securityAccount)
      janusDataBucket <- getIamUnrecognisedUserBucket
      janusDataFileKey <- getJanusDataFileKey
      s3Object <- getS3Object(client, janusDataBucket, janusDataFileKey)
      janusDataRaw = s3Object.mkString
      janusData = JanusConfig.load(janusDataRaw)
      janusUsernames = getJanusUsernames(janusData)
      accountCredsReports = getCredsReportDisplayForAccount(allCredsReports)
      accountUnrecognisedUsers = accountCredsReports.map { case (acc, crd) => (acc, filterUnrecognisedIamUsers(crd.humanUsers, janusUsernames))}
      notificationIds <- Attempt.traverse(accountUnrecognisedUsers)(disableUser(_, testMode))
    } yield notificationIds
    result.fold(
      { failure =>
        logger.error(s"Failed to run unrecognised user job: ${failure.logMessage}")
      },
      { notificationIds =>
        logger.info(s"Successfully ran unrecognised user job and sent ${notificationIds.length} notifications.")
      }
    )
  }

  private def disableUser(accountCrd: (Account, Seq[VulnerableUser]), testMode: Boolean): Attempt[String] = {
    val (account, users) = accountCrd
    disableAccessKeys(account, users, iamClients)
    removePasswords(account, users, iamClients)
    sendNotification(account, users, testMode)
  }

  private def getS3Object(s3Client: AwsClient[AmazonS3], bucket: String, key: String): Attempt[BufferedSource] = {
    Attempt.Right(
      scala.io.Source
        .fromInputStream(s3Client.client.getObject(bucket, key).getObjectContent)
    )
  }

  private def getJanusDataFileKey: Attempt[String] = {
    Attempt.fromOption(
      Config.getIamUnrecognisedUserS3Key(config),
      FailedAttempt(Failure("unable to get janus data file key from config for the IAM unrecognised job",
        "unable to get janus data file key from config for the IAM unrecognised job",
        500)
      )
    )
  }

  private def getIamUnrecognisedUserBucket: Attempt[String] = {
    Attempt.fromOption(
      Config.getIamUnrecognisedUserS3Bucket(config),
      FailedAttempt(Failure("unable to get IAM unrecognised user bucket from config",
        "unable to get IAM unrecognised user bucket from config",
        500)
      )
    )
  }

  private def getSecurityAccount: Attempt[Account] = {
    Attempt.fromOption(
      Config.getAwsAccounts(config).find(_.name == "security"),
      FailedAttempt(Failure("unable to find security account details from config",
        "unable to find security account details from config",
        500))
    )
  }

  def sendNotification(account: Account, unrecognisedIamUsers: Seq[VulnerableUser], testMode: Boolean): Attempt[String] = {
    val message = notification(
      disabledUsersSubject(account),
      disabledUsersMessage(unrecognisedIamUsers),
      List(TargetAccount(account.accountNumber))
    )
    send(message, topicArn, snsClient, testMode)
  }
}
