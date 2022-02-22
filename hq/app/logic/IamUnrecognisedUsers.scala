package logic

import aws.AwsAsyncHandler.{awsToScala}
import aws.{AwsAsyncHandler, AwsClient, AwsClients}
import aws.iam.IAMClient.SOLE_REGION
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
  ): List[(AwsAccount, List[HumanUser])] = {
    val unrecognisedUsers = accountCredsReports.map { case (acc, crd) => (acc, filterUnrecognisedIamUsers(crd.humanUsers, janusUsernames)) }
    val unrecognisedUsersInAllowedAccounts = filterAllowedAccounts(unrecognisedUsers, allowedAccountIds)
    unrecognisedUsersInAllowedAccounts.filter { case (_, users) => users.nonEmpty } // remove accounts with an empty list of users
  }

  private def filterUnrecognisedIamUsers(iamHumanUsersWithTargetTag: Seq[HumanUser], janusUsernames: List[String]): List[HumanUser] =
    iamHumanUsersWithTargetTag.filterNot { iamUser =>
      val maybeTag = iamUser.tags.find(tag => tag.key == USERNAME_TAG_KEY)
      maybeTag match {
        case Some(tag) => janusUsernames.contains(tag.value) // filter out human users that have tags which match the janus usernames
        case None => true
      }
    }.toList

  private def filterAllowedAccounts(
    unrecognisedUsers: List[(AwsAccount, List[HumanUser])],
    allowedAccountIds: List[String]
  ): List[(AwsAccount, List[HumanUser])] = {
    unrecognisedUsers.filter { case (account, _) => allowedAccountIds.contains(account.id) }
  }

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

  def disableUser(
    accountCrd: (AwsAccount, List[HumanUser]),
    iamClients: AwsClients[AmazonIdentityManagementAsync]
  )(implicit executionContext: ExecutionContext): Attempt[List[String]] = {
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

  def unrecognisedUserNotifications(accountUsers: List[(AwsAccount, List[HumanUser])]): List[Notification] = {
    accountUsers.flatMap { case (account, users) =>
      users.map { user =>
        unrecognisedUserRemediation(account, user)
      }
    }
  }

  def disableAccessKeys(
    account: AwsAccount,
    vulnerableUsers: List[HumanUser],
    iamClients: AwsClients[AmazonIdentityManagementAsync]
  )(implicit ec: ExecutionContext): Attempt[List[UpdateAccessKeyResult]] = {
    val result = for {
      accessKeys <- listAccountAccessKeys(account, vulnerableUsers, iamClients)
      activeAccessKeys = accessKeys.filter(_.accessKeyWithId.accessKey.keyStatus == AccessKeyEnabled)
      updateAccessKeyRequests = activeAccessKeys.map(updateAccessKeyRequest)
      client <- iamClients.get(account, SOLE_REGION)
      updateAccessKeyResults <- Attempt.traverse(updateAccessKeyRequests)(req => AwsAsyncHandler.handleAWSErrs(client)(awsToScala(client)(_.updateAccessKeyAsync)(req)))
    } yield updateAccessKeyResults
    result.fold(
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
    )
    result
  }

  private def updateAccessKeyRequest(key: VulnerableAccessKey): UpdateAccessKeyRequest = {
    new UpdateAccessKeyRequest()
      .withUserName(key.username)
      .withAccessKeyId(key.accessKeyWithId.id)
      .withStatus("Inactive")
  }

  def removePasswords(
    account: AwsAccount,
    user: HumanUser,
    iamClients: AwsClients[AmazonIdentityManagementAsync]
  )(implicit ec: ExecutionContext): Attempt[Option[DeleteLoginProfileResult]] = {
    val result: Attempt[Option[DeleteLoginProfileResult]] = for {
      client <- iamClients.get(account, SOLE_REGION)
      request = new DeleteLoginProfileRequest().withUserName(user.username)
      response = awsToScala(client)(_.deleteLoginProfileAsync)(request)
      deleteResult <- handleAWSErrs(client, user)(response)
    } yield deleteResult
    result.tap {
      case Left(failure) =>
        logger.error(s"failed to delete password for username: ${user.username}. ${failure.logMessage}")
        Cloudwatch.putIamRemovePasswordMetric(ReaperExecutionStatus.failure)
      case Right(success) =>
        logger.info(s"password deleted for ${user.username}. DeleteLoginProfile Response: ${success.map(_.getSdkResponseMetadata.getRequestId)}.")
        Cloudwatch.putIamRemovePasswordMetric(ReaperExecutionStatus.success)
    }
  }

  def handleAWSErrs(awsClient: AwsClient[AmazonIdentityManagementAsync], user: HumanUser)(f: => Future[DeleteLoginProfileResult])(implicit ec: ExecutionContext): Attempt[Option[DeleteLoginProfileResult]] =
    AwsAsyncHandler.handleAWSErrs(awsClient)(f.map(Some.apply).recover({
      case e if e.getMessage.contains(s"Login Profile for User ${user.username} cannot be found") => None
      case _: NoSuchEntityException => None
    }))
}
