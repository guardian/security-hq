package logic

import aws.iam.IAMClient
import aws.{AWS, AwsClients}
import com.typesafe.scalalogging.LazyLogging
import config.CoreConfig
import db.IamRemediationDb
import logic.IamOutdatedCredentials.*
import logic.IamUnrecognisedUsers.*
import model.*
import notifications.AnghammaradNotifications
import org.joda.time.DateTime
import settings.Settings
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.iam.IamAsyncClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sns.SnsAsyncClient
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.ListHasAsScala

class IamOutdatedCredentials(
    snsClient: SnsAsyncClient,
    iamClients: AwsClients[IamAsyncClient],
    dynamo: IamRemediationDb,
    dryRun: Boolean = true
) extends LazyLogging {

  /** Performs the specified operation, which will be one of:
    *   - send a warning
    *   - send a final warning
    *   - disable an IAM credential and send a notification that this has been done
    *   - remove an IAM password and send a notification that this has been done
    */
  private def performRemediationOperation(
      remediationOperation: RemediationOperation,
      now: DateTime,
      notificationTopicArn: String,
      tableName: String,
      dryRun: Boolean
  )(implicit ec: ExecutionContext): Attempt[String] = {
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
      case (Warning, OutdatedCredential) if !dryRun =>
        val notification =
          AnghammaradNotifications.outdatedCredentialWarning(awsAccount, iamUser, problemCreationDate, now)
        for {
          snsId <- AnghammaradNotifications.send(notification, notificationTopicArn, snsClient)
          _ <- dynamo.writeRemediationActivity(thisRemediationActivity, tableName)
        } yield snsId

      case (Warning, OutdatedCredential) =>
        logger.info(s"Dry run: Would send warning for $awsAccount, $iamUser")
        Attempt.Right("dummy-warning")

      case (FinalWarning, OutdatedCredential) if !dryRun =>
        val notification =
          AnghammaradNotifications.outdatedCredentialFinalWarning(awsAccount, iamUser, problemCreationDate, now)
        for {
          snsId <- AnghammaradNotifications.send(notification, notificationTopicArn, snsClient)
          _ <- dynamo.writeRemediationActivity(thisRemediationActivity, tableName)
        } yield snsId

      case (FinalWarning, OutdatedCredential) =>
        logger.info(s"Dry run: Would send final warning for $awsAccount, $iamUser")
        Attempt.Right("dummy-finalWarning")

      case (Remediation, OutdatedCredential) if !dryRun =>
        val notification =
          AnghammaradNotifications.outdatedCredentialRemediation(awsAccount, iamUser, problemCreationDate)
        for {
          // disable the correct credential
          userCredentialInformation <- IAMClient.listUserAccessKeys(awsAccount, iamUser, iamClients)
          credentialToDisable <- lookupCredentialId(problemCreationDate, userCredentialInformation)
          _ <- IAMClient.disableAccessKey(
            awsAccount,
            credentialToDisable.username,
            credentialToDisable.accessKeyId,
            iamClients
          )
          // send a notification to say this is what we have done
          notificationId <- AnghammaradNotifications.send(notification, notificationTopicArn, snsClient)
          // save a record of the change
          _ <- dynamo.writeRemediationActivity(thisRemediationActivity, tableName)
        } yield notificationId

      case (Remediation, OutdatedCredential) =>
        logger.info(s"Dry run: Would execute remediation for $awsAccount, $iamUser")
        Attempt.Right("dummy-remediation")
    }
  }

  /** We only perform actions on accounts that are explicitly allowed, but it is helpful to log the operation that
    * *would* have been performed, if allowed.
    */
  private def dummyOperation(remediationOperation: RemediationOperation): Unit = {
    val awsAccount = remediationOperation.vulnerableCandidate.awsAccount
    logger.warn(s"Remediation operation skipped because ${awsAccount.id} is not configured for remediation")
    logger.warn(s"Skipping remediation action: ${formatRemediationOperation(remediationOperation)}")
  }

  /** If an AWS access key has not been rotated in a long time, then will automatically disable it.
    *
    * This job will first send a warning notification when it detects an outdated credential. If nothing changes it will
    * send a final warning notification. After both these notifications have been ignored, the credential will be
    * automatically disabled.
    */
  def disableOutdatedCredentials(
      notificationTopicArn: String,
      tableName: String,
      serviceAccountIds: List[String],
      rawCredsReports: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]],
      allowedAwsAccountIds: List[String]
  )(implicit ec: ExecutionContext): Attempt[Unit] = {
    val now = new DateTime()
    val accountsCredReports = getCredsReportDisplayForAccount(rawCredsReports)
    val accountUsersWithOutdatedCredentials = identifyAllUsersWithOutdatedCredentials(accountsCredReports, now)

    val result = for {
      // fetch IAM data from the application cache
      // identify users with outdated credentials for each account, from the credentials report
      // DB lookup of previous SHQ activity for each user to produce a list of "candidate" vulnerabilities
      vulnerabilitiesWithRemediationHistory <- lookupActivityHistory(
        accountUsersWithOutdatedCredentials,
        dynamo,
        tableName
      )
      // based on activity history, decide which of these candidates have outstanding SHQ operations
      outstandingOperations = calculateOutstandingAccessKeyOperations(vulnerabilitiesWithRemediationHistory, now)
      // we'll only perform operations on accounts that have been configured as eligible
      filteredOperations = partitionOperationsByAllowedAccounts(
        outstandingOperations,
        allowedAwsAccountIds,
        serviceAccountIds
      )
      // we won't execute these operations, but can log them instead
      _ = filteredOperations.operationsOnAccountsThatAreNotAllowed.foreach(dummyOperation)
      // now we know what operations need to be performed, so let's run each of those
      results <- Attempt.traverse(filteredOperations.allowedOperations)(
        performRemediationOperation(_, now, notificationTopicArn, tableName, dryRun)
      )
    } yield results
    result.tap {
      case Left(failedAttempt) =>
        logger.error(
          s"Failure during 'disable outdated credentials' job: ${failedAttempt.logMessage}",
          failedAttempt.firstException.orNull // make sure the exception goes into the log, if present
        )
      case Right(operationIds) =>
        logger.info(
          s"Successfully completed 'disable outdated credentials' job, with ${operationIds.length} operations"
        )
    }.unit
  }

}
object IamOutdatedCredentials extends LazyLogging {

  // TODO this code does not close the SnsAsyncClient or S3Client, or any of the IAM/CFN clients.
  // In practice, the lambda will exit after this job is complete, so the resources will be cleaned up by the OS,
  // but it would be better to close them explicitly.  This is not trivial to do, because the clients are created
  // sequentially, and the SnsAsyncClient is used in the middle of the process.  We could use a Resource pattern
  // to manage this better.

  def disableOutdatedCredentials(settings: Settings)(implicit executionContext: ExecutionContext): Attempt[Unit] = {
    val snsClient = SnsAsyncClient.builder().build()
    val s3Client = S3Client.builder.build()

    val awsAccountsConfig = CoreConfig.loadConfigFromS3(settings.configBucket, settings.configKey, s3Client)
    val awsAccounts = CoreConfig.parseAccounts(awsAccountsConfig)
    val anghammaradSnsArn = awsAccountsConfig.getString(ANGHAMMARAD_SNS_TOPIC_ARN_CONFIG_ITEM)
    val iamDynamoTableName = awsAccountsConfig.getString(IAM_DYNAMO_TABLE_NAME_CONFIG_ITEM)
    val allowedAccountIds: List[String] =
      awsAccountsConfig.getStringList(ALLOWED_ACCOUNT_IDS_CONFIG_ITEM).asScala.toList
    val accountIdsForIamRemediationService =
      awsAccountsConfig.getStringList(REMEDIATION_ACCOUNT_IDS_CONFIG_ITEM).asScala.toList
    val dynamo = new IamRemediationDb(CoreConfig.getSecurityDynamoDbClient(settings.stage))

    for {
      availableRegions: List[Region] <- CoreConfig.calculateAvailableRegions(settings.stack, settings.stage)
      iamClients = AWS.iamClients(awsAccounts, availableRegions)
      cfnClients = AWS.cfnClients(awsAccounts, availableRegions)
      reportAttemptsList <- Attempt.traverseWithFailures(awsAccounts) { account =>
        IAMClient.getUpdatedCredentialsReport(account, cfnClients, iamClients, availableRegions)
      }

      listOfCredentialReports = awsAccounts.zip(reportAttemptsList).toMap

      disableResult <- new IamOutdatedCredentials(
        snsClient = snsClient,
        iamClients = iamClients,
        dynamo = dynamo,
        dryRun = settings.dryRun
      ).disableOutdatedCredentials(
        notificationTopicArn = anghammaradSnsArn,
        tableName = iamDynamoTableName,
        serviceAccountIds = accountIdsForIamRemediationService,
        rawCredsReports = listOfCredentialReports,
        allowedAwsAccountIds = allowedAccountIds
      )
    } yield disableResult
  }

  /** Look through all credentials reports to find users with expired credentials, see below for more detail
    * (`identifyUsersWithOutdatedCredentials`).
    */
  private def identifyAllUsersWithOutdatedCredentials(
      accountCredentialReports: List[(AwsAccount, CredentialReportDisplay)],
      now: DateTime
  ): List[(AwsAccount, List[IAMUser])] = {
    accountCredentialReports.map { case (awsAccount, credentialReport) =>
      (awsAccount, identifyUsersWithOutdatedCredentials(credentialReport, now))
    }
  }

  /** Looks through the credentials report to identify users with Access Keys that are older than we allow.
    */
  private[logic] def identifyUsersWithOutdatedCredentials(
      credentialReportDisplay: CredentialReportDisplay,
      now: DateTime
  ): List[IAMUser] = {
    val machineUsersWithOutdatedKeys =
      credentialReportDisplay.machineUsers.filter(user => hasOutdatedMachineKey(List(user.key1, user.key2), now))
    val humanUsersWithOutdatedKeys =
      credentialReportDisplay.humanUsers.filter(user => hasOutdatedHumanKey(List(user.key1, user.key2), now))

    // Filter out any users tagged with opt-out tag, so we can enable the job on accounts
    // while attempting to rotate difficult keys. This should be used as a last resort only.
    (machineUsersWithOutdatedKeys ++ humanUsersWithOutdatedKeys).toList
      .filterNot(_.tags.exists(_.key == CoreConfig.outdatedCredentialOptOutUserTag))
  }

  private def hasOutdatedHumanKey(keys: List[AccessKey], now: DateTime): Boolean =
    keys.exists(isOutdatedHumanKey(_, now))

  private def hasOutdatedMachineKey(keys: List[AccessKey], now: DateTime): Boolean =
    keys.exists(isOutdatedMachineKey(_, now))

  private def isOutdatedHumanKey(key: AccessKey, now: DateTime): Boolean = {
    key.lastRotated.exists { date =>
      // using minus 1 so that we return true if the last rotated date is exactly on the cadence date
      date.isBefore(now.minusDays(CoreConfig.iamHumanUserRotationCadence.toInt - 1))
    } && key.keyStatus == AccessKeyEnabled
  }

  private def isOutdatedMachineKey(key: AccessKey, now: DateTime): Boolean = {
    key.lastRotated.exists { date =>
      // using minus 1 so that we return true if the last rotated date is exactly on the cadence date
      date.isBefore(now.minusDays(CoreConfig.iamMachineUserRotationCadence.toInt - 1))
    } && key.keyStatus == AccessKeyEnabled
  }

  /** Given an IAMUser (in an AWS account), look up that user's activity history form the Database.
    */
  private def lookupActivityHistory(
      accountIdentifiedUsers: List[(AwsAccount, List[IAMUser])],
      dynamo: IamRemediationDb,
      tableName: String
  )(implicit ec: ExecutionContext): Attempt[List[IamUserRemediationHistory]] = {
    for {
      remediationHistoryByAccount <- Attempt.traverse(accountIdentifiedUsers) { case (awsAccount, identifiedUsers) =>
        // for each account with vulnerable user(s), do a DB lookup for each identified user to get activity history
        Attempt.traverse(identifiedUsers) { identifiedUser =>
          dynamo.lookupIamUserNotifications(identifiedUser, awsAccount, tableName).map { userActivityHistory =>
            IamUserRemediationHistory(awsAccount, identifiedUser, userActivityHistory)
          }
        }
      }
    } yield {
      // no need to have these separated by account any more
      remediationHistoryByAccount.flatten
    }
  }

  /** Looks through the candidate's remediation history and outputs the work to be done per access key. This means that
    * the same user could appear in the output list twice, because both of their keys may require an operation. By
    * comparing the current date with the date of the most recent activity, we know which operation to perform next.
    */
  private[logic] def calculateOutstandingAccessKeyOperations(
      remediationHistories: List[IamUserRemediationHistory],
      now: DateTime
  ): List[RemediationOperation] = {
    for {
      userRemediationHistory <- remediationHistories
      vulnerableKey <- identifyVulnerableKeys(userRemediationHistory, now)
      keyPreviousAlert = identifyMostRecentActivity(userRemediationHistory, vulnerableKey)
      keyNextActivity <- identifyRemediationOperation(keyPreviousAlert, now, userRemediationHistory, vulnerableKey)
    } yield keyNextActivity
  }

  private[logic] def identifyVulnerableKeys(
      remediationHistory: IamUserRemediationHistory,
      now: DateTime
  ): List[AccessKey] = {
    val user = remediationHistory.iamUser
    if (user.isHuman) List(user.key1, user.key2).filter(isOutdatedHumanKey(_, now))
    else List(user.key1, user.key2).filter(isOutdatedMachineKey(_, now))
  }

  private[logic] def identifyMostRecentActivity(
      remediationHistory: IamUserRemediationHistory,
      vulnerableKey: AccessKey
  ): Option[IamRemediationActivity] = {
    // TODO lastRotatedDate should not be an Option, because every IAM access key has a last rotated date. Change SHQ's model.
    vulnerableKey.lastRotated match {
      case Some(lastRotatedDate) =>
        // filter activity list to find matching db records for given access key
        val keyPreviousActivities =
          remediationHistory.activityHistory.filter(_.problemCreationDate.isEqual(lastRotatedDate))
        keyPreviousActivities match {
          case Nil =>
            // there is no recent activity for the given access key, so return None.
            None
          case remediationActivities =>
            // get the most recent remediation activity
            Some(remediationActivities.maxBy(_.dateNotificationSent.getMillis))
        }
      case None =>
        val name = remediationHistory.iamUser.username
        val account = remediationHistory.awsAccount.name
        logger.warn(s"$name in $account has an access key without a lastRotatedDate. Please investigate.")
        None
    }
  }

  private[logic] def identifyRemediationOperation(
      mostRecentRemediationActivity: Option[IamRemediationActivity],
      now: DateTime,
      userRemediationHistory: IamUserRemediationHistory,
      vulnerableKey: AccessKey
  ): Option[RemediationOperation] =
    mostRecentRemediationActivity match {
      case None =>
        // If there is no recent activity, then the required operation must be a Warning.
        Some(
          RemediationOperation(
            userRemediationHistory,
            Warning,
            OutdatedCredential,
            problemCreationDate = vulnerableKey.lastRotated.getOrElse(now)
          )
        )
      case Some(mostRecentActivity) =>
        val finalWarningStartOfDay = mostRecentActivity.dateNotificationSent
          .plusDays(CoreConfig.daysBetweenWarningAndFinalNotification)
          .withTimeAtStartOfDay()
        val remediationStartOfDay = mostRecentActivity.dateNotificationSent
          .plusDays(CoreConfig.daysBetweenFinalNotificationAndRemediation)
          .withTimeAtStartOfDay()
        mostRecentActivity.iamRemediationActivityType match {
          case Warning if now.isAfter(finalWarningStartOfDay) =>
            // If the most recent activity is a Warning and the last notification was sent at least `Config.daysBetweenWarningAndFinalNotification` ago,
            // the required operation is a FinalWarning.
            Some(
              RemediationOperation(
                userRemediationHistory,
                FinalWarning,
                mostRecentActivity.iamProblem,
                mostRecentActivity.problemCreationDate
              )
            )
          case FinalWarning if now.isAfter(remediationStartOfDay) =>
            // If the most recent activity is a FinalWarning and the last notification was sent at least `Config.daysBetweenFinalNotificationAndRemediation` ago,
            // the required operation is Remediation.
            Some(
              RemediationOperation(
                userRemediationHistory,
                Remediation,
                mostRecentActivity.iamProblem,
                mostRecentActivity.problemCreationDate
              )
            )
          case Remediation =>
            val name = userRemediationHistory.iamUser.username
            val account = userRemediationHistory.awsAccount.name
            logger.warn(
              s"$name in $account has an access key of recent activity type Remediation, but the key is enabled. Please investigate as I will continue to attempt key disablement until rotated."
            )
            Some(
              RemediationOperation(
                userRemediationHistory,
                Remediation,
                mostRecentActivity.iamProblem,
                mostRecentActivity.problemCreationDate
              )
            )
          case _ => None
        }
    }

  /** To prevent non-PROD application instances from making changes to production AWS accounts, SHQ is configured with a
    * list of the AWS accounts that this instance is allowed to affect. In addition, SHQ is configured with a list of
    * AWS accounts to run the IamRemediationService on, because not all accounts are in a ready state for this service
    * yet.
    */
  private[logic] def partitionOperationsByAllowedAccounts(
      operations: List[RemediationOperation],
      allowedAwsAccountIds: List[String],
      serviceAccountIds: List[String]
  ): PartitionedRemediationOperations = {
    val (allowed, forbidden) = operations.partition { remediationOperation =>
      val accountId = remediationOperation.vulnerableCandidate.awsAccount.id
      allowedAwsAccountIds.contains(accountId) && serviceAccountIds.contains(accountId)
    }
    PartitionedRemediationOperations(allowed, forbidden)
  }

  /** Users can have multiple credentials, and it can be tricky to know which one we have identified. From the full
    * metadata for all a user's keys, we can look up the AccessKey's ID by comparing the creation dates with the key we
    * are expecting.
    *
    * This might fail, because it may be that no matching key exists.
    */
  private[logic] def lookupCredentialId(
      badKeyCreationDate: DateTime,
      userCredentials: List[CredentialMetadata]
  ): Attempt[CredentialMetadata] = {
    val username = userCredentials.map(_.username).headOption.getOrElse("unknown username")
    userCredentials.filter { credentialMetadata =>
      credentialMetadata.creationDate.withMillisOfSecond(0) == badKeyCreationDate.withMillisOfSecond(0)
    } match {
      case singleMatchingKey :: Nil => Attempt.Right(singleMatchingKey)
      case Nil                      =>
        Attempt.Left(
          FailedAttempt(
            Failure(
              "unable to identify matching access key in user's metadata",
              s"I've made a list-access-keys AWS API call for $username, but I have not found a matching key in the response.",
              500
            )
          )
        )
      case _ =>
        // This is an edge case where both the user's access keys both have the same creation date.
        // This should be unlikely given the Credentials Reports creation dates are defined up to the second.
        Attempt.Left(
          FailedAttempt(
            Failure(
              s"both of $username's access keys have the exact same creation date - cannot decide which one to select for disablement",
              s"I've hit an edge case for $username's access keys where both have the same creation date, please investigate as I can't decide which one to disable.",
              500
            )
          )
        )
    }
  }

  private[logic] def formatRemediationOperation(remediationOperation: RemediationOperation): String = {
    val problem = remediationOperation.iamProblem
    val activity = remediationOperation.iamRemediationActivityType
    val username = remediationOperation.vulnerableCandidate.iamUser.username
    val accountId = remediationOperation.vulnerableCandidate.awsAccount.id
    s"$problem $activity for user $username from account $accountId"
  }

  private val REMEDIATION_ACCOUNT_IDS_CONFIG_ITEM = "ACCOUNT_IDS_FOR_IAM_REMEDIATION_SERVICE"
  private val ALLOWED_ACCOUNT_IDS_CONFIG_ITEM = "ALLOWED_ACCOUNT_IDS"
  private val ANGHAMMARAD_SNS_TOPIC_ARN_CONFIG_ITEM = "ANGHAMMARAD_SNS_TOPIC_ARN"
  private val IAM_DYNAMO_TABLE_NAME_CONFIG_ITEM = "IAM_DYNAMO_TABLE_NAME"

}
