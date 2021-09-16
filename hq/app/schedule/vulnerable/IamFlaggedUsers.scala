package schedule.vulnerable

import logic.VulnerableAccessKeys
import model.{AwsAccount, CredentialReportDisplay, VulnerableUser}
import play.api.Logging
import utils.attempt.FailedAttempt

/**
  * This object filters the AWS IAM credentials reports for permanent credentials which are old or missing multi-factor authentication,
  * so that Security HQ can alert the AWS accounts holding these vulnerable credentials if needed.
  */
object IamFlaggedUsers extends Logging {

  def getVulnerableUsers(allCreds: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]]): Map[AwsAccount, Seq[VulnerableUser]] = {
    // Error handling for when no credentials report. N.B. could be placed elsewhere
    allCreds.foreach { case (awsAccount, eitherReportOrFailure) =>
      eitherReportOrFailure.left.foreach { error =>
        error.failures.foreach { failure =>
          val errorMessage = s"failed to collect credentials report display for ${awsAccount.name}: ${failure.friendlyMessage}"
          failure.throwable.fold(logger.error(errorMessage))(throwable => logger.error(errorMessage, throwable))
        }
      }
    }

    // Filter out any accounts we failed to grab the credentials report for
    allCreds.collect { case (awsAccount, Right(report)) => (awsAccount, findVulnerableUsers(report)) }
  }

  private[vulnerable] def findVulnerableUsers(report: CredentialReportDisplay): Seq[VulnerableUser] =
    (findOldAccessKeys(report) union findMissingMfa(report)).distinct

  private[vulnerable] def findOldAccessKeys(credsReport: CredentialReportDisplay): Seq[VulnerableUser] = {
    val filteredMachines = credsReport.machineUsers.filter(user => VulnerableAccessKeys.hasOutdatedMachineKey(List(user.key1, user.key2)))
    val filteredHumans = credsReport.humanUsers.filter(user => VulnerableAccessKeys.hasOutdatedHumanKey(List(user.key1, user.key2)))
    (filteredMachines ++ filteredHumans).map(VulnerableUser.fromIamUser)
  }

  private[vulnerable] def findMissingMfa(credsReport: CredentialReportDisplay): Seq[VulnerableUser] = {
    val filteredHumans = credsReport.humanUsers.filterNot(_.hasMFA)
    filteredHumans.map(VulnerableUser.fromIamUser)
  }
}
