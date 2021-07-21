package schedule

import config.Config.{iamHumanUserRotationCadence, iamMachineUserRotationCadence}
import logic.DateUtils
import model._
import play.api.Logging
import schedule.IamDeadline.filterUsersToAlert
import schedule.IamTargetGroups.getNotificationTargetGroups
import utils.attempt.FailedAttempt

/**
  * This object filters the AWS IAM credentials reports for permanent credentials which are old or missing multi-factor authentication,
  * so that Security HQ can alert the AWS accounts holding these vulnerable credentials if needed.
  */
object IamFlaggedUsers extends Logging {

  def getFlaggedCredentialsReports(allCreds: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]], dynamo: Dynamo): Map[AwsAccount, Seq[IAMAlertTargetGroup]] = {
    allCreds.map { case (awsAccount, maybeReport) => maybeReport match {
      case Left(error) =>
        error.failures.foreach { failure =>
          val errorMessage = s"failed to collect credentials report display for ${awsAccount.name}: ${failure.friendlyMessage}"
          failure.throwable.fold(logger.error(errorMessage))(throwable => logger.error(errorMessage, throwable))
        }
        (awsAccount, Left(error))
      case Right(report) =>
        // alert the Ophan AWS account using tags to ensure that the Ophan and Data Tech teams who share the same AWS account receive the right emails
        if (awsAccount.name == "Ophan") (awsAccount, Right(getTargetGroups(report, awsAccount, dynamo)))
        else (awsAccount, Right(Seq(IAMAlertTargetGroup(List.empty, getUsersToAlert(report, awsAccount, dynamo)))))
    }
    }.collect { case (awsAccount, Right(report)) => (awsAccount, report) }
  }

  def getUsersToAlert(report: CredentialReportDisplay, awsAccount: AwsAccount, dynamo: Dynamo): Seq[VulnerableUser] = {
    val vulnerableUsers = findVulnerableUsers(report)
    filterUsersToAlert(vulnerableUsers, awsAccount, dynamo)
  }

  def getTargetGroups(report: CredentialReportDisplay, awsAccount: AwsAccount, dynamo: Dynamo): Seq[IAMAlertTargetGroup] = {
    getNotificationTargetGroups(getUsersToAlert(report, awsAccount, dynamo))
  }

  def findVulnerableUsers(report: CredentialReportDisplay): Seq[VulnerableUser] = {
    outdatedKeysInfo(findOldAccessKeys(report)) ++ missingMfaInfo(findMissingMfa(report))
  }

  def findOldAccessKeys(credsReport: CredentialReportDisplay): CredentialReportDisplay = {
    val filteredMachines = credsReport.machineUsers.filter(user => hasOutdatedMachineKey(List(user.key1, user.key2)))
    val filteredHumans = credsReport.humanUsers.filter(user => hasOutdatedHumanKey(List(user.key1, user.key2)))
    credsReport.copy(machineUsers = filteredMachines, humanUsers = filteredHumans)
  }

  def hasOutdatedHumanKey(keys: List[AccessKey]): Boolean = keys.exists(key => DateUtils.dayDiff(key.lastRotated).getOrElse(1L) > iamHumanUserRotationCadence)
  def hasOutdatedMachineKey(keys: List[AccessKey]): Boolean = keys.exists(key => DateUtils.dayDiff(key.lastRotated).getOrElse(1L) > iamMachineUserRotationCadence)

  def findMissingMfa(credsReport: CredentialReportDisplay): CredentialReportDisplay = {
    val removeMachineUsers = credsReport.machineUsers.filterNot(_.username == "")
    val filteredHumans = credsReport.humanUsers.filterNot(_.hasMFA)
    credsReport.copy(machineUsers = removeMachineUsers, humanUsers = filteredHumans)
  }

  def outdatedKeysInfo(users: CredentialReportDisplay): Seq[VulnerableUser] = {
    val machines = users.machineUsers.map { user =>
      VulnerableUser(
        user.username,
        user.tags
      )
    }
    val humans = users.humanUsers.map { user =>
      VulnerableUser(
        user.username,
        user.tags
      )
    }
    machines ++ humans
  }

  def missingMfaInfo(users: CredentialReportDisplay): Seq[VulnerableUser] = {
    users.humanUsers.map { user =>
      VulnerableUser(
        user.username,
        user.tags
      )
    }
  }
}
