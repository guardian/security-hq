package schedule.vulnerable

import logic.VulnerableAccessKeys
import model.{AwsAccount, CredentialReportDisplay, IAMAlertTargetGroup, VulnerableUser}
import play.api.Logging
import schedule.Dynamo
import schedule.IamTargetGroups.getNotificationTargetGroups
import schedule.vulnerable.IamDeadline.filterUsersToAlert
import utils.attempt.FailedAttempt

/**
  * This object filters the AWS IAM credentials reports for permanent credentials which are old or missing multi-factor authentication,
  * so that Security HQ can alert the AWS accounts holding these vulnerable credentials if needed.
  */
object IamFlaggedUsers extends Logging {

  def getVulnerableUsers(allCreds: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]]): Map[AwsAccount, Seq[IAMAlertTargetGroup]] = {
    allCreds.map { case (awsAccount, maybeReport) => maybeReport match {
      case Left(error) =>
        error.failures.foreach { failure =>
          val errorMessage = s"failed to collect credentials report display for ${awsAccount.name}: ${failure.friendlyMessage}"
          failure.throwable.fold(logger.error(errorMessage))(throwable => logger.error(errorMessage, throwable))
        }
        (awsAccount, Left(error))
      case Right(report) =>
        // alert the Ophan AWS account using tags to ensure that the Ophan and Data Tech teams who share the same AWS account receive the right emails
        if (awsAccount.name == "Ophan") (awsAccount, Right(getNotificationTargetGroups(findVulnerableUsers(report))))
        else {
          (awsAccount, Right(Seq(IAMAlertTargetGroup(List.empty, findVulnerableUsers(report)))))
        }
    }
    }.collect { case (awsAccount, Right(report)) => (awsAccount, report) }
  }

  def getVulnerableUsersToAlert(users: Map[AwsAccount, Seq[IAMAlertTargetGroup]], dynamo: Dynamo): Map[AwsAccount, Seq[IAMAlertTargetGroup]] = {
    users.map { case (account, targetGroups) =>
      account -> targetGroups.map { tg =>
        tg.copy(users = filterUsersToAlert(targetGroups.flatMap(_.users), account, dynamo))
      }
    }
  }

  private def findVulnerableUsers(report: CredentialReportDisplay): Seq[VulnerableUser] = {
    outdatedKeysInfo(findOldAccessKeys(report))
      .union(missingMfaInfo(findMissingMfa(report)))
      .distinct
  }

  def findOldAccessKeys(credsReport: CredentialReportDisplay): CredentialReportDisplay = {
    val filteredMachines = credsReport.machineUsers.filter(user => VulnerableAccessKeys.hasOutdatedMachineKey(List(user.key1, user.key2)))
    val filteredHumans = credsReport.humanUsers.filter(user => VulnerableAccessKeys.hasOutdatedHumanKey(List(user.key1, user.key2)))
    credsReport.copy(machineUsers = filteredMachines, humanUsers = filteredHumans)
  }

  def findMissingMfa(credsReport: CredentialReportDisplay): CredentialReportDisplay = {
    val removeMachineUsers = credsReport.machineUsers.filterNot(_.username == "")
    val filteredHumans = credsReport.humanUsers.filterNot(_.hasMFA)
    credsReport.copy(machineUsers = removeMachineUsers, humanUsers = filteredHumans)
  }

  private def outdatedKeysInfo(users: CredentialReportDisplay): Seq[VulnerableUser] = {
    (users.machineUsers ++ users.humanUsers).map(VulnerableUser.fromIamUser)
  }

  private def missingMfaInfo(users: CredentialReportDisplay): Seq[VulnerableUser] = {
    users.humanUsers.map(VulnerableUser.fromIamUser)
  }
}
