package logic

import aws.AwsClients
import aws.iam.IAMClient.listUserAccessKeys
import com.gu.anghammarad.models.Notification
import com.gu.janus.model.JanusData
import com.typesafe.scalalogging.LazyLogging
import model.*
import notifications.AnghammaradNotifications.unrecognisedUserRemediation
import utils.attempt.{Attempt, FailedAttempt}

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.concurrent.ExecutionContext
import software.amazon.awssdk.services.iam.IamAsyncClient

object IamUnrecognisedUsers extends LazyLogging {
  val USERNAME_TAG_KEY = "GoogleUsername"

  def getJanusUsernames(janusData: JanusData): List[String] =
    janusData.access.userAccess.keys.toList

  /** Removes the FailedAttempts from the Either and returns a list of tuples with only the Right values. This function
    * uses generics to make it easier to test, but to avoid confusion it was written to take a Map of AWSAccount to
    * Either and return a list of tuples of AWS Account to CredentialReportDisplay.
    */
  def getCredsReportDisplayForAccount[B](allCreds: Map[AwsAccount, Either[FailedAttempt, B]]): List[(AwsAccount, B)] = {
    allCreds.toList.foldLeft[List[(AwsAccount, B)]](Nil) {
      case (acc, (account, Left(failure))) =>
        failure.firstException match {
          case Some(cause) =>
            logger.error(
              s"unable to generate credential report display for account ${account.name}: ${failure.logMessage}",
              cause
            )
          case None =>
            logger.error(
              s"unable to generate credential report display for account ${account.name}: ${failure.logMessage}"
            )
        }
        acc
      case (acc, (account, Right(credReportDisplay))) =>
        (account, credReportDisplay) :: acc
    }
  }

  /** Returns IAM permanent credentials for people who are not janus users. Filters for the accounts the Security HQ
    * stage has been configured for - see "alert.allowedAccountIds" in configuration.
    */
  def unrecognisedUsersForAllowedAccounts(
      accountCredsReports: List[(AwsAccount, CredentialReportDisplay)],
      janusUsernames: List[String],
      allowedAccountIds: List[String]
  ): List[AccountUnrecognisedUsers] = {
    for {
      (acc, crd) <- accountCredsReports
      accountUsers = AccountUnrecognisedUsers(acc, filterUnrecognisedIamUsers(crd.humanUsers, janusUsernames))
      accountId = accountUsers.account.id
      if accountUsers.unrecognisedUsers.nonEmpty && allowedAccountIds.contains(accountId)
    } yield accountUsers
  }

  private def filterUnrecognisedIamUsers(
      iamHumanUsersWithTargetTag: Seq[HumanUser],
      janusUsernames: List[String]
  ): List[HumanUser] =
    iamHumanUsersWithTargetTag.filterNot { iamUser =>
      val maybeTag = iamUser.tags.find(tag => tag.key == USERNAME_TAG_KEY)
      maybeTag match {
        case Some(tag) =>
          janusUsernames.contains(tag.value) // filter out human users that have tags which match the janus usernames
        case None => true
      }
    }.toList

  def makeFile(s3Object: String): File = {
    Files
      .write(
        Files.createTempFile("janusData", ".txt"),
        s3Object.getBytes(StandardCharsets.UTF_8)
      )
      .toFile
  }

  def isTaggedForUnrecognisedUser(tags: List[Tag]): Boolean = {
    tags.exists(t =>
      t.key == USERNAME_TAG_KEY &&
        t.value != "" &&
        t.value.contains(".")
    )
  }

  def listAccountAccessKeys(
      accountUnrecognisedUsers: AccountUnrecognisedUsers,
      iamClients: AwsClients[IamAsyncClient]
  )(implicit ec: ExecutionContext): Attempt[AccountUnrecognisedAccessKeys] = {
    val AccountUnrecognisedUsers(account, users) = accountUnrecognisedUsers
    Attempt.flatTraverse(users)(listUserAccessKeys(account, _, iamClients)).map {
      AccountUnrecognisedAccessKeys(account, _)
    }
  }

  def unrecognisedUserNotifications(
      accountUsers: List[AccountUnrecognisedUsers],
      dryRun: Boolean
  ): List[Notification] = {
    if (!dryRun) {
      accountUsers.flatMap { case AccountUnrecognisedUsers(account, users) =>
        users.map { user =>
          unrecognisedUserRemediation(account, user)
        }
      }
    } else {
      logger.info(s"DRY RUN: Would send ${accountUsers.length} notification(s).")
      Nil
    }
  }
}
