package logic

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.{AwsAsyncHandler, AwsClient, AwsClients}
import aws.iam.IAMClient.{SOLE_REGION, deleteLoginProfile, disableAccessKey}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{DeleteLoginProfileRequest, DeleteLoginProfileResult, NoSuchEntityException, UpdateAccessKeyRequest, UpdateAccessKeyResult}
import com.gu.anghammarad.models.Notification
import com.gu.janus.model.JanusData
import logging.Cloudwatch
import logging.Cloudwatch.ReaperExecutionStatus
import logic.IamListAccessKeys.listAccountAccessKeys
import model._
import notifications.AnghammaradNotifications.unrecognisedUserRemediation
import play.api.Logging
import utils.attempt.{Attempt, FailedAttempt}

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.concurrent.{ExecutionContext, Future}

object IamUnrecognisedUsers extends Logging {
  val USERNAME_TAG_KEY = "GoogleUsername"

  def getJanusUsernames(janusData: JanusData): List[String] =
    janusData.access.userAccess.keys.toList

  /**
    * Removes the FailedAttempts from the Either and returns a list of tuples with only the Right values.
    * This function uses generics to make it easier to test, but to avoid confusion it was written to take
    * a Map of AWSAccount to Either and return a list of tuples of AWS Account to CredentialReportDisplay.
    */
  def getCredsReportDisplayForAccount[A, B](allCreds: Map[A, Either[FailedAttempt, B]]): List[(A, B)] = {
    allCreds.toList.foldLeft[List[(A, B)]](Nil) {
      case (acc, (_, Left(failure))) =>
        logger.error(s"unable to generate credential report display: ${failure.logMessage}")
        acc
      case (acc, (account, Right(credReportDisplay))) =>
        (account, credReportDisplay) :: acc
    }
  }

  /**
    * Returns IAM permanent credentials for people who are not janus users.
    * Filters for the accounts the Security HQ stage has been configured for - see "alert.allowedAccountIds" in configuration.
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

  private def filterUnrecognisedIamUsers(iamHumanUsersWithTargetTag: Seq[HumanUser], janusUsernames: List[String]): List[HumanUser] =
    iamHumanUsersWithTargetTag.filterNot { iamUser =>
      val maybeTag = iamUser.tags.find(tag => tag.key == USERNAME_TAG_KEY)
      maybeTag match {
        case Some(tag) => janusUsernames.contains(tag.value) // filter out human users that have tags which match the janus usernames
        case None => true
      }
    }.toList

  def makeFile(s3Object: String): File = {
    Files.write(
      Files.createTempFile("janusData", ".txt"),
      s3Object.getBytes(StandardCharsets.UTF_8)
    ).toFile
  }

  def isTaggedForUnrecognisedUser(tags: List[Tag]): Boolean = {
    tags.exists(t =>
      t.key == USERNAME_TAG_KEY &&
        t.value != "" &&
        t.value.contains(".")
    )
  }

  def disableAccountAccessKeys(
    accountUnrecognisedKeys: AccountUnrecognisedAccessKeys,
    iamClients: AwsClients[AmazonIdentityManagementAsync]
  )(implicit ec: ExecutionContext): Attempt[List[UpdateAccessKeyResult]] = {
    val AccountUnrecognisedAccessKeys(account, accessKeys) = accountUnrecognisedKeys
    val activeAccessKeys = accessKeys.filter(_.status == CredentialActive)
    val disableKeysAttempt = Attempt.traverse(activeAccessKeys)(key =>
      disableAccessKey(account, key.username, key.accessKeyId, iamClients)
    )

    disableKeysAttempt.tap(_.fold(
      { failure =>
        logger.error(s"Failed to disable access key: ${failure.logMessage}")
        Cloudwatch.putIamDisableAccessKeyMetric(ReaperExecutionStatus.failure)
      },
      { updateAccessKeyResults =>
        logger.info(s"Attempt to disable access keys was successful. ${updateAccessKeyResults.length} key(s) were disabled in ${account.name}.")
        if(updateAccessKeyResults.nonEmpty) {
          Cloudwatch.putIamDisableAccessKeyMetric(ReaperExecutionStatus.success)
        }
      }
    ))
  }

  def removeAccountPasswords(
    accountUnrecognisedUsers: AccountUnrecognisedUsers,
    iamClients: AwsClients[AmazonIdentityManagementAsync]
  )(implicit ec: ExecutionContext): Attempt[List[Option[DeleteLoginProfileResult]]] = {
    val results = Attempt.traverse(accountUnrecognisedUsers.unrecognisedUsers)(user => deleteLoginProfile(accountUnrecognisedUsers.account, user.username, iamClients))
    results.tap {
      case Left(failure) =>
        logger.error(s"failed to delete at least one password: ${failure.logMessage}")
        Cloudwatch.putIamRemovePasswordMetric(ReaperExecutionStatus.failure, 1)
      case Right(success) =>
        logger.info(s"passwords deleted for ${accountUnrecognisedUsers.unrecognisedUsers.map(_.username).mkString(",")}")
        Cloudwatch.putIamRemovePasswordMetric(ReaperExecutionStatus.success, success.flatten.length)
    }
  }

  def unrecognisedUserNotifications(accountUsers: List[AccountUnrecognisedUsers]): List[Notification] = {
    accountUsers.flatMap { case AccountUnrecognisedUsers(account, users) =>
      users.map { user =>
        unrecognisedUserRemediation(account, user)
      }
    }
  }
}
