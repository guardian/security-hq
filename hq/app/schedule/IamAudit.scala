package schedule

import com.gu.anghammarad.models.{Notification, AwsAccount => Account}
import config.Config.{iamHumanUserRotationCadence, iamMachineUserRotationCadence}
import logic.DateUtils
import model._
import play.api.Logging
import schedule.IamMessages.createMessage
import schedule.IamNotifier.createNotification
import utils.attempt.FailedAttempt

object IamAudit extends Logging {
  def makeCredentialsNotification(allCreds: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]]):List[Notification] = {
    allCreds.toList.flatMap { case (awsAccount, eFCreds) =>
      eFCreds match {
        case Right(credsReport) =>
          val outdatedKeys = outdatedKeysInfo(findOldAccessKeys(credsReport))
          val missingMfa = missingMfaInfo(findMissingMfa(credsReport))
          if (outdatedKeys.isEmpty && missingMfa.isEmpty) {
            logger.info(s"found no IAM user issues for ${awsAccount.name}. No notification required.")
            None
          } else {
            logger.info(s"for ${awsAccount.name}, generating iam notification message for ${outdatedKeys.length} user(s) with outdated keys and ${missingMfa.length} user(s) with missing mfa")
            val message = createMessage(outdatedKeys, missingMfa, awsAccount)
            Some(createNotification(awsAccount, Account(awsAccount.accountNumber), message))
          }
        case Left(error) =>
          error.failures.foreach { failure =>
            val errorMessage = s"failed to collect credentials report for IAM notifier: ${failure.friendlyMessage}"
            failure.throwable.fold(logger.error(errorMessage))(throwable => logger.error(errorMessage, throwable))
          }
          None
      }
    }
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

  def outdatedKeysInfo(outdatedKeys: CredentialReportDisplay): Seq[UserWithOutdatedKeys] = {
    val machines = outdatedKeys.machineUsers.map { user =>
      UserWithOutdatedKeys(
        user.username,
        user.key1.lastRotated,
        user.key2.lastRotated,
        user.lastActivityDay
      )
    }
    val humans = outdatedKeys.humanUsers.map { user =>
      UserWithOutdatedKeys(
        user.username,
        user.key1.lastRotated,
        user.key2.lastRotated,
        user.lastActivityDay
      )
    }
    machines ++ humans
  }

  def missingMfaInfo(missingMfa: CredentialReportDisplay): Seq[UserNoMfa] = {
    missingMfa.humanUsers.map { user =>
      UserNoMfa(
        user.username,
        user.lastActivityDay
      )
    }
  }
}
