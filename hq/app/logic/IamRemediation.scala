package logic

import config.Config
import db.IamRemediationDb
import model.{CredentialMetadata, IamUserRemediationHistory, PartitionedRemediationOperations, RemediationOperation}
import model.{AccessKey, AccessKeyEnabled, AwsAccount, CredentialReportDisplay, IAMUser}
import org.joda.time.DateTime
import play.api.Logging
import utils.attempt.{Attempt, FailedAttempt}

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
      (awsAccount, identifyUsersWithOutdatedCredentials(awsAccount, credentialReport, now))
    }
  }

  /**
    * Looks through the credentials report to identify users with Access Keys that are older than we allow.
    */
  def identifyUsersWithOutdatedCredentials(awsAccount: AwsAccount, credentialReportDisplay: CredentialReportDisplay, now: DateTime): List[IAMUser] = {
    credentialReportDisplay.machineUsers.filter(user => hasOutdatedMachineKey(List(user.key1, user.key2), now)).toList ++
      credentialReportDisplay.humanUsers.filter(user => hasOutdatedHumanKey(List(user.key1, user.key2), now))
  }

  private def hasOutdatedHumanKey(keys: List[AccessKey], now: DateTime): Boolean = keys.exists { key =>
      key.lastRotated.exists { date =>
        // using minus 1 so that we return true if the last rotated date is exactly on the cadence date
        date.isBefore(now.minusDays(Config.iamHumanUserRotationCadence.toInt - 1))
      } && key.keyStatus == AccessKeyEnabled
    }

  private def hasOutdatedMachineKey(keys: List[AccessKey], now: DateTime): Boolean = keys.exists { key =>
      key.lastRotated.exists { date =>
        // using minus 1 so that we return true if the last rotated date is exactly on the cadence date
        date.isBefore(now.minusDays(Config.iamMachineUserRotationCadence.toInt - 1))
      } && key.keyStatus == AccessKeyEnabled
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
    * Looks through the candidates with their remediation history to decide what work needs to be done.
    *
    * By comparing the current date with
    */
  def calculateOutstandingOperations(remediationHistory: List[IamUserRemediationHistory], now: DateTime): List[RemediationOperation] = {
    ???
  }

  /**
    * To prevent non-PROD application instances from making changes to production AWS accounts, SHQ is
    * configured with a list of the AWS accounts that this instance is allowed to affect.
    */
  def partitionOperationsByAllowedAccounts(operations: List[RemediationOperation], allowedAwsAccountIds: List[String]): PartitionedRemediationOperations = {
    val (allowed, forbidden) = operations.partition(remediationOperation => allowedAwsAccountIds.contains(remediationOperation.vulnerableCandidate.awsAccount.id))
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
    ???
  }

  def formatRemediationOperation(remediationOperation: RemediationOperation): String = {
    ???
  }
}
