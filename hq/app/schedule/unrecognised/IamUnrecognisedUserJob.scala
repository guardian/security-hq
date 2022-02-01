package schedule.unrecognised

import aws.AwsClients
import aws.s3.S3.getS3Object
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.models.{AwsAccount => TargetAccount}
import com.gu.janus.JanusConfig
import config.Config.getIamUnrecognisedUserConfig
import model.{AccessKeyEnabled, VulnerableUser, AwsAccount => Account}
import org.joda.time.{DateTime, DateTimeConstants}
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Environment, Logging, Mode}
import rx.lang.scala.{Observable, Subscription}
import schedule.IamMessages.FormerStaff.disabledUsersMessage
import schedule.IamMessages.disabledUsersSubject
import schedule.Notifier.{notification, send}
import schedule.unrecognised.IamUnrecognisedUsers.{getCredsReportDisplayForAccount, getJanusUsernames, makeFile, unrecognisedUsersForAllowedAccounts}
import schedule.vulnerable.IamDisableAccessKeys.disableAccessKeys
import schedule.vulnerable.IamRemovePassword.removePasswords
import services.CacheService
import utils.attempt.Attempt

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class IamUnrecognisedUserJob(
  cacheService: CacheService,
  snsClient: AmazonSNSAsync,
  securityS3Client: AmazonS3,
  iamClients: AwsClients[AmazonIdentityManagementAsync],
  config: Configuration,
  environment: Environment,
  lifecycle: ApplicationLifecycle
)(implicit executionContext: ExecutionContext) extends Logging {

  def run(): Unit = {
    val result: Attempt[List[Option[String]]] = for {
      config <- getIamUnrecognisedUserConfig(config)
      s3Object <- getS3Object(securityS3Client, config.janusUserBucket, config.janusDataFileKey)
      janusData = JanusConfig.load(makeFile(s3Object.mkString))
      janusUsernames = getJanusUsernames(janusData)
      accountCredsReports = getCredsReportDisplayForAccount(cacheService.getAllCredentials)
      allowedAccountsUnrecognisedUsers = unrecognisedUsersForAllowedAccounts(accountCredsReports, janusUsernames, config.allowedAccounts)
      _ <- Attempt.traverse(allowedAccountsUnrecognisedUsers)(disableUser)
      notificationIds <- Attempt.traverse(allowedAccountsUnrecognisedUsers)(sendNotification(_, environment.mode == Mode.Test, config.anghammaradSnsTopicArn))
    } yield notificationIds
    result.fold(
      { failure =>
        logger.error(s"Failed to run unrecognised user job: ${failure.logMessage}")
      },
      { notificationIds =>
        logger.info(s"Successfully ran unrecognised user job and sent ${notificationIds.flatten.length} notifications.")
      }
    )
  }

  private def disableUser(accountCrd: (Account, List[VulnerableUser])): Attempt[List[String]] = {
    val (account, users) = accountCrd
    for {
      disableKeyResult <- disableAccessKeys(account, users, iamClients)
      removePasswordResults <- Attempt.traverse(users)(removePasswords(account, _, iamClients))
    } yield {
      disableKeyResult.map(_.getSdkResponseMetadata.getRequestId) ++ removePasswordResults.collect {
          case Some(result) => result.getSdkResponseMetadata.getRequestId
        }
    }
  }

  private def sendNotification(accountCrd: (Account, Seq[VulnerableUser]), testMode: Boolean, topicArn: String): Attempt[Option[String]] = {
    val (account, users) = accountCrd
    val usersWithAtLeastOneEnabledKeyOrHuman = users.filter( user =>
      user.key1.keyStatus == AccessKeyEnabled ||
        user.key2.keyStatus == AccessKeyEnabled ||
        user.humanUser
    )
      if (usersWithAtLeastOneEnabledKeyOrHuman.isEmpty) {
      Attempt.Right(None)
    } else {
      val message = notification(
        disabledUsersSubject(account),
        disabledUsersMessage(usersWithAtLeastOneEnabledKeyOrHuman),
        List(TargetAccount(account.accountNumber))
      )
      send(message, topicArn, snsClient, testMode).map(Some(_))
    }
  }

  // Schedule on weekdays at 10am
  if (environment.mode != Mode.Test) {
    val disableUnrecognisedCredentials: Observable[DateTime] = Observable.interval(10.minutes, 1.minute)
      .map(_ => DateTime.now())
      .filterNot { now =>
        now.getDayOfWeek == DateTimeConstants.SATURDAY || now.getDayOfWeek == DateTimeConstants.SUNDAY
      }
      .filter(now => now.getHourOfDay == 10 && now.getMinuteOfHour == 0)

    val subscription: Subscription = disableUnrecognisedCredentials.subscribe { _ =>
      run()
    }

    lifecycle.addStopHook { () =>
      subscription.unsubscribe()
      Future.successful(())
    }
  }
}
