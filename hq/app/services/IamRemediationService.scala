package services

import aws.AwsClients
import aws.iam.IAMClient
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.sns.AmazonSNSAsync
import config.Config.getAllowedAccountsForStage
import db.IamRemediationDb
import logic.IamRemediation._
import model.iamremediation._
import notifications.AnghammaradNotifications
import org.joda.time.DateTime
import play.api.{Configuration, Logging}
import utils.attempt.Attempt

import scala.concurrent.ExecutionContext

/**
  * A collection of jobs for automatically fixing IAM problems in our AWS accounts.
  *
  * These jobs either directly intervene to fix security misconfigurations, or they do so
  * only after sending warning notifications to the account administrators.
  *
  * In cases where notifications are a requirement, DynamoDB is used to store a log of what
  * notifications have been sent.
  */
class IamRemediationService(
  cacheService: CacheService, snsClient: AmazonSNSAsync, dynamo: IamRemediationDb,
  config: Configuration, iamClients: AwsClients[AmazonIdentityManagementAsync],
  notificationTopicArn: String
) extends Logging {

  /**
    * If an AWS access key has not been rotated in a long time, then will automatically disable it.
    *
    * This job will first send a warning notification when it detects an outdated credential.
    * If nothing changes it will send a final warning notification.
    * After both these notifications have been ignored, the credential will be automatically disabled.
    */
  def disableOutdatedCredentials()(implicit ec: ExecutionContext): Attempt[Unit] = {
    val now = new DateTime()
    val result = for {
      // lookup essential configuration
      allowedAwsAccountIds <- getAllowedAccountsForStage(config) // this tells us which AWS accounts we are allowed to make changes to
      // fetch IAM data from the application cache
      rawCredsReports = cacheService.getAllCredentials
      accountsCredReports = getCredsReportDisplayForAccount(rawCredsReports)
      // identify users with outdated credentials for each account, from the credentials report
      accountUsersWithOutdatedCredentials = identifyAllUsersWithOutdatedCredentials(accountsCredReports, now)
      // DB lookup of previous SHQ activity for each user to produce a list of "candidate" vulnerabilities
      vulnerabilitiesWithRemediationHistory <- lookupActivityHistory(accountUsersWithOutdatedCredentials, dynamo)
      // based on activity history, decide which of these candidates have outstanding SHQ operations
      outstandingOperations = calculateOutstandingOperations(vulnerabilitiesWithRemediationHistory, now)
      // we'll only perform operations on accounts that have been configured as eligible
      filteredOperations = partitionOperationsByAllowedAccounts(outstandingOperations, allowedAwsAccountIds)
      // we won't execute these operations, but can log them instead
      _ = filteredOperations.operationsOnAccountsThatAreNotAllowed.foreach(dummyOperation)
      // now we know what operations need to be performed, so let's run each of those
      results <- Attempt.traverse(filteredOperations.allowedOperations)(performRemediationOperation(_, now))
    } yield results
    result.tap {
      case Left(failedAttempt) =>
        logger.error(
          s"Failure during 'disable outdated credentials' job: ${failedAttempt.logMessage}",
          failedAttempt.firstException.orNull  // make sure the exception goes into the log, if present
        )
      case Right(operationIds) =>
        logger.info(s"Successfully completed 'disable outdated credentials' job, with ${operationIds.length} operations")
    }.unit
  }

  /**
    * Disables password access for users that do not also have MFA enabled.
    *
    * This job will first send a warning notification when it detects a password without MFA.
    * If nothing changes it will send a final warning notification.
    * After both these notifications have been ignored, the password will be automatically removed.
    *
    * TODO: we will implement this job after the outdated credentials job ships
    */
  def removePasswordWithoutMFA(): Attempt[Unit] = ???

  /**
    * Removes AWS access for colleagues that have departed.
    *
    * This feature is targeted at "recovery access", where teams keep one or two IAM users that can be
    * used to gain access to AWS when Janus is down. These recovery users have a password (and MFA) but
    * do not have credentials. They should also be tagged with the Google username of the individual so
    * we can identify them.
    *
    * We then load data from the Guardian's Janus configuration and decide who us "recognised" by comparing
    * this data with google identity tags. If we find an IAM user tagged with an identity that is not in
    * Janus, we can assume they have left and disable the IAM user.
    *
    */
  def disableUnrecognisedUsers(): Attempt[Unit] = ???

  /**
    * Performs the specified operation, which will be one of:
    * - send a warning
    * - send a final warning
    * - disable an IAM credential and send a notification that this has been done
    * - remove an IAM password and send a notification that this has been done
    */
  def performRemediationOperation(remediationOperation: RemediationOperation, now: DateTime)(implicit ec: ExecutionContext): Attempt[String] = {
    val awsAccount = remediationOperation.vulnerableCandidate.awsAccount
    val iamUser = remediationOperation.vulnerableCandidate.iamUser
    val problemCreationDate = remediationOperation.problemCreationDate
    // if successful, this record will be added to the database
    val thisRemediationActivity = IamRemediationActivity(
      awsAccount.id,
      iamUser.username,
      now,
      remediationOperation.iamRemediationActivityType,
      remediationOperation.iamProblem,
      remediationOperation.problemCreationDate
    )

    (remediationOperation.iamRemediationActivityType, remediationOperation.iamProblem) match {
    // Outdated credentials
      case (Warning, OutdatedCredential) =>
        val notification = AnghammaradNotifications.outdatedCredentialWarning(awsAccount, iamUser, problemCreationDate)
        for {
          snsId <- AnghammaradNotifications.send(notification, notificationTopicArn, snsClient)
          _ <- dynamo.writeRemediationActivity(thisRemediationActivity)
        } yield snsId

      case (FinalWarning, OutdatedCredential) =>
        val notification = AnghammaradNotifications.outdatedCredentialFinalWarning(awsAccount, iamUser, problemCreationDate)
        for {
          snsId <- AnghammaradNotifications.send(notification, notificationTopicArn, snsClient)
          _ <- dynamo.writeRemediationActivity(thisRemediationActivity)
        } yield snsId

      case (Remediation, OutdatedCredential) =>
        val notification = AnghammaradNotifications.outdatedCredentialRemediation(awsAccount, iamUser, problemCreationDate)
        for {
          // disable the correct credential
          userCredentialInformation <- IAMClient.listUserAccessKeys(awsAccount, iamUser, iamClients)
          credentialToDisable <- lookupCredentialId(problemCreationDate, userCredentialInformation)
          _ <- IAMClient.disableAccessKey(awsAccount, credentialToDisable.username, credentialToDisable.accessKeyId, iamClients)
          // send a notification to say this is what we have done
          notificationId <- AnghammaradNotifications.send(notification, notificationTopicArn, snsClient)
          // save a record of the change
          _ <- dynamo.writeRemediationActivity(thisRemediationActivity)
        } yield notificationId

    // passwords without MFA
      case (Warning, PasswordMissingMFA) =>
        val notification = AnghammaradNotifications.passwordWithoutMfaWarning(awsAccount, iamUser, problemCreationDate)
        for {
          snsId <- AnghammaradNotifications.send(notification, notificationTopicArn, snsClient)
          _ <- dynamo.writeRemediationActivity(thisRemediationActivity)
        } yield snsId

      case (FinalWarning, PasswordMissingMFA) =>
        val notification = AnghammaradNotifications.passwordWithoutMfaFinalWarning(awsAccount, iamUser, problemCreationDate)
        for {
          snsId <- AnghammaradNotifications.send(notification, notificationTopicArn, snsClient)
          _ <- dynamo.writeRemediationActivity(thisRemediationActivity)
        } yield snsId

      case (Remediation, PasswordMissingMFA) =>
        val notification = AnghammaradNotifications.passwordWithoutMfaRemediation(awsAccount, iamUser, problemCreationDate)
        for {
          // TODO: add functionality to disable the user's password when we implement this job
          notificationId <- AnghammaradNotifications.send(notification, notificationTopicArn, snsClient)
          _ <- dynamo.writeRemediationActivity(thisRemediationActivity)
        } yield notificationId
    }
  }

  /**
    * We only perform actions on accounts that are explicitly allowed, but it is helpful
    * to log the operation that *would* have been performed, if allowed.
    */
  def dummyOperation(remediationOperation: RemediationOperation): Unit = {
    val awsAccount = remediationOperation.vulnerableCandidate.awsAccount
    logger.warn(s"Remediation operation skipped because ${awsAccount.id} is not configured for remediation")
    logger.warn(s"Skipping remediation action: ${formatRemediationOperation(remediationOperation)}")
  }
}
