package logic

import config.Config
import config.Config.{daysBetweenFinalNotificationAndRemediation, daysBetweenWarningAndFinalNotification}
import db.IamRemediationDb
import model._
import org.joda.time.{DateTime, Days}
import play.api.Logging
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.ExecutionContext


object IamRemediation extends Logging {
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
    * Look through all credentials reports to find users with expired credentials,
    * see below for more detail (`identifyUsersWithOutdatedCredentials`).
    */
  def identifyAllUsersWithOutdatedCredentials(accountCredentialReports: List[(AwsAccount, CredentialReportDisplay)], now: DateTime): List[(AwsAccount, List[IAMUser])] = {
    accountCredentialReports.map { case (awsAccount, credentialReport) =>
      (awsAccount, identifyUsersWithOutdatedCredentials(credentialReport, now))
    }
  }

  /**
    * Looks through the credentials report to identify users with Access Keys that are older than we allow.
    */
  private[logic] def identifyUsersWithOutdatedCredentials(credentialReportDisplay: CredentialReportDisplay, now: DateTime): List[IAMUser] = {
    credentialReportDisplay.machineUsers.filter(user => hasOutdatedMachineKey(List(user.key1, user.key2), now)).toList ++
      credentialReportDisplay.humanUsers.filter(user => hasOutdatedHumanKey(List(user.key1, user.key2), now))
  }

  private def hasOutdatedHumanKey(keys: List[AccessKey], now: DateTime): Boolean = keys.exists(isOutdatedHumanKey(_, now))

  private def hasOutdatedMachineKey(keys: List[AccessKey], now: DateTime): Boolean = keys.exists(isOutdatedMachineKey(_, now))

  private def isOutdatedHumanKey(key: AccessKey, now: DateTime): Boolean = {
    key.lastRotated.exists { date =>
      // using minus 1 so that we return true if the last rotated date is exactly on the cadence date
      date.isBefore(now.minusDays(Config.iamHumanUserRotationCadence.toInt - 1))
    } && key.keyStatus == AccessKeyEnabled
  }

  private def isOutdatedMachineKey(key: AccessKey, now: DateTime): Boolean = {
    key.lastRotated.exists { date =>
      // using minus 1 so that we return true if the last rotated date is exactly on the cadence date
      date.isBefore(now.minusDays(Config.iamMachineUserRotationCadence.toInt - 1))
    } && key.keyStatus == AccessKeyEnabled
  }

  def identityAllUsersWithPasswordMissingMFA(accountCredentialReports: List[(AwsAccount, CredentialReportDisplay)], now: DateTime): List[(AwsAccount, List[IAMUser])] = {
    accountCredentialReports.map { case (awsAccount, credentialReport) =>
      (awsAccount, identityUsersWithPasswordMissingMFA(credentialReport))
    }
  }

  /**
   * Looks through the credentials report to identify users with passwords, but no MFA
   */
  private[logic] def identityUsersWithPasswordMissingMFA(credentialReportDisplay: CredentialReportDisplay): List[IAMUser] = {
    credentialReportDisplay.humanUsers.filterNot(_.hasMFA).toList
  }

  /**
    * Given an IAMUser (in an AWS account), look up that user's activity history form the Database.
    */
  def lookupActivityHistory(accountIdentifiedUsers: List[(AwsAccount, List[IAMUser])], dynamo: IamRemediationDb)(implicit ec: ExecutionContext): Attempt[List[IamUserRemediationHistory]] = {
    for {
      remediationHistoryByAccount <- Attempt.traverse(accountIdentifiedUsers) { case (awsAccount, identifiedUsers) =>
        // for each account with vulnerable user(s), do a DB lookup for each identified user to get activity history
        Attempt.traverse(identifiedUsers) { identifiedUser =>
          dynamo.lookupIamUserNotifications(identifiedUser, awsAccount).map { userActivityHistory =>
            IamUserRemediationHistory(awsAccount, identifiedUser, userActivityHistory)
          }
        }
      }
    } yield {
      // no need to have these separated by account any more
      remediationHistoryByAccount.flatten
    }
  }

  /**
    * Looks through the candidate's remediation history and outputs the work to be done per access key.
    * This means that the same user could appear in the output list twice, because both of their keys may require an operation.
    * By comparing the current date with the date of the most recent activity, we know which operation to perform next.
    */
  def calculateOutstandingAccessKeyOperations(remediationHistories: List[IamUserRemediationHistory], now: DateTime): List[RemediationOperation] = {
    for {
      userRemediationHistory <- remediationHistories
      vulnerableKey <- identifyVulnerableKeys(userRemediationHistory, now)
      keyPreviousAlert = identifyMostRecentActivity(userRemediationHistory, vulnerableKey)
      keyNextActivity <- identifyRemediationOperation(keyPreviousAlert, now, userRemediationHistory)
    } yield keyNextActivity
  }

  private[logic] def identifyVulnerableKeys(remediationHistory: IamUserRemediationHistory, now: DateTime): List[AccessKey] = {
    val user = remediationHistory.iamUser
    if (user.isHuman) List(user.key1, user.key2).filter(isOutdatedHumanKey(_, now))
    else List(user.key1, user.key2).filter(isOutdatedMachineKey(_, now))
  }

  private[logic] def identifyMostRecentActivity(remediationHistory: IamUserRemediationHistory, vulnerableKey: AccessKey): Option[IamRemediationActivity] = {
    //TODO lastRotatedDate should not be an Option, because every IAM access key has a last rotated date. Change SHQ's model.
    vulnerableKey.lastRotated match {
      case Some(lastRotatedDate) =>
        // filter activity list to find matching db records for given access key
        val keyPreviousActivities = remediationHistory.activityHistory.filter { activity =>
          activity.problemCreationDate.withTimeAtStartOfDay == lastRotatedDate.withTimeAtStartOfDay()
        }
        keyPreviousActivities match {
          case Nil =>
            // there is no recent activity for the given access key, so return None.
            None
          case remediationActivities =>
            // get the most recent remediation activity
            Some(remediationActivities.maxBy { activity =>
              Days.daysBetween(activity.problemCreationDate.withTimeAtStartOfDay(), activity.dateNotificationSent.withTimeAtStartOfDay()).getDays
            })
        }
      case None =>
        val name = remediationHistory.iamUser.username
        val account = remediationHistory.awsAccount.name
        logger.warn(s"$name in $account has an access key without a lastRotatedDate. Please investigate.")
        None
    }
  }

  /**
   * Looks through the candidate's remediation history and outputs the operations to be done.
   */
  def calculateOutstandingPasswordOperations(remediationHistories: List[IamUserRemediationHistory], now: DateTime): List[RemediationOperation] = ???

  private[logic] def identifyRemediationOperation(mostRecentRemediationActivity: Option[IamRemediationActivity], now: DateTime,
    userRemediationHistory: IamUserRemediationHistory): Option[RemediationOperation] =
    mostRecentRemediationActivity match {
      case None =>
        // If there is no recent activity, then the required operation must be a Warning.
        Some(RemediationOperation(userRemediationHistory, Warning, OutdatedCredential, problemCreationDate = now))
      case Some(mostRecentActivity) =>
        mostRecentActivity.iamRemediationActivityType match {
        case Warning if now.isAfter(mostRecentActivity.dateNotificationSent.plusDays(daysBetweenWarningAndFinalNotification - 1)) =>
          // If the most recent activity is a Warning and the last notification was sent at least `Config.daysBetweenWarningAndFinalNotification` ago,
          // the required operation is a FinalWarning.
          Some(RemediationOperation(userRemediationHistory, FinalWarning, mostRecentActivity.iamProblem, mostRecentActivity.problemCreationDate))
        case FinalWarning if now.isAfter(mostRecentActivity.dateNotificationSent.plusDays(daysBetweenFinalNotificationAndRemediation - 1)) =>
          // If the most recent activity is a FinalWarning and the last notification was sent at least `Config.daysBetweenFinalNotificationAndRemediation` ago,
          // the required operation is Remediation.
          Some(RemediationOperation(userRemediationHistory, Remediation, mostRecentActivity.iamProblem, mostRecentActivity.problemCreationDate))
        case Remediation =>
          val name = userRemediationHistory.iamUser.username
          val account = userRemediationHistory.awsAccount.name
          logger.warn(s"$name in $account has an access key of recent activity type Remediation, but the key is enabled. Please investigate as I will continue to attempt key disablement until rotated.")
          Some(RemediationOperation(userRemediationHistory, Remediation, mostRecentActivity.iamProblem, mostRecentActivity.problemCreationDate))
        case _ => None
      }
    }

  /**
    * To prevent non-PROD application instances from making changes to production AWS accounts, SHQ is
    * configured with a list of the AWS accounts that this instance is allowed to affect.
    */
  def partitionOperationsByAllowedAccounts(operations: List[RemediationOperation], allowedAwsAccountIds: List[String]): PartitionedRemediationOperations = {
    val (allowed, forbidden) = operations.partition { remediationOperation =>
      allowedAwsAccountIds.contains(remediationOperation.vulnerableCandidate.awsAccount.id)
    }
    PartitionedRemediationOperations(allowed, forbidden)
  }

  /**
    * Users can have multiple credentials, and it can be tricky to know which one we have identified.
    * From the full metadata for all a user's keys, we can look up the AccessKey's ID by comparing the
    * creation dates with the key we are expecting.
    *
    * This might fail, because it may be that no matching key exists.
    */
  def lookupCredentialId(badKeyCreationDate: DateTime, userCredentials: List[CredentialMetadata]): Attempt[CredentialMetadata] = {
    val username = userCredentials.map(_.username).headOption.getOrElse("unknown username")
    userCredentials.filter { credentialMetadata =>
      credentialMetadata.creationDate.withMillisOfSecond(0) == badKeyCreationDate.withMillisOfSecond(0)
    } match {
      case singleMatchingKey :: Nil => Attempt.Right(singleMatchingKey)
      case Nil =>
        Attempt.Left(FailedAttempt(Failure(
          "unable to identify matching access key in user's metadata",
          s"I've made a list-access-keys AWS API call for $username, but I have not found a matching key in the response.",
          500
        )))
      case _ =>
        // This is an edge case where both the user's access keys both have the same creation date.
        // This should be unlikely given the Credentials Reports creation dates are defined up to the second.
        Attempt.Left(FailedAttempt(Failure(
          s"both of $username's access keys have the exact same creation date - cannot decide which one to select for disablement",
          s"I've hit an edge case for $username's access keys where both have the same creation date, please investigate as I can't decide which one to disable.",
          500
        )))
    }
  }

  def formatRemediationOperation(remediationOperation: RemediationOperation): String = {
    val problem = remediationOperation.iamProblem
    val activity = remediationOperation.iamRemediationActivityType
    val username = remediationOperation.vulnerableCandidate.iamUser.username
    val accountId = remediationOperation.vulnerableCandidate.awsAccount.id
    s"$problem $activity for user $username from account $accountId"
  }
}
