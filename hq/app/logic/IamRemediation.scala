package logic

import com.gu.anghammarad.models.Notification
import db.IamRemediationDb
import model.iamremediation.{CredentialMetadata, IamProblem, IamRemediationActivityType, PartitionedRemediationOperations, RemediationHistory, RemediationOperation}
import model.{AwsAccount, CredentialReportDisplay, IAMUser}
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
    * Look through all the credentials reports to
    */
  def identifyAllUsersWithOutdatedCredentials(accountCredentialReports: List[(AwsAccount, CredentialReportDisplay)]): List[(AwsAccount, List[IAMUser])] = {
    accountCredentialReports.map { case (awsAccount, credentialReport) =>
      (awsAccount, identifyUsersWithOutdatedCredentials(awsAccount, credentialReport))
    }
  }

  def identifyUsersWithOutdatedCredentials(awsAccount: AwsAccount, credentialReportDisplay: CredentialReportDisplay): List[IAMUser] = {
    ???
  }

  /**
    * Given an IAMUser (in an AWS account), look up that user's activity history form the Database.
    */
  def lookupActivityHistory(accountIdentifiedUsers: List[(AwsAccount, List[IAMUser])], dynamo: IamRemediationDb)(implicit ec: ExecutionContext): Attempt[List[RemediationHistory]] = {
    Attempt.traverse(accountIdentifiedUsers) { case (awsAccount, identifiedUsers) =>
      Attempt.traverse(identifiedUsers) { identifiedUser =>
        dynamo.lookupIamUserNotifications(identifiedUser, awsAccount).map { userActivityHistory =>
          RemediationHistory(awsAccount, identifiedUser, userActivityHistory)
        }
      }
    }.map(_.flatten)
  }

  /**
    * Looks through the candidates with their remediation history to decide what work needs to be done.
    */
  def calculateOutstandingOperations(remediationHistory: List[RemediationHistory]): List[RemediationOperation] = {
    ???
  }

  def partitionOperationsByAllowedAccounts(operations: List[RemediationOperation]): PartitionedRemediationOperations = {
    ???
  }

  /**
    * Users can have multiple credentials, and it can be tricky to know which one we have identified.
    * From the full metadata for all a user's keys, we can look up the AccessKey's ID by comparing the
    * creation dates with the key we are expecting.
    *
    * This might fail, because it may be that no matchign key exists.
    */
  def lookupCredentialId(badKeyCreationDate: DateTime, userCredentials: List[CredentialMetadata]): Attempt[CredentialMetadata] = {
    ???
  }

  def formatRemediationOperation(remediationOperation: RemediationOperation): String = {
    ???
  }
}
