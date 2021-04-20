package schedule

import com.gu.anghammarad.models.{Notification, AwsAccount => Account}
import logic.DateUtils
import model._
import schedule.IamMessages.createMessage
import schedule.IamNotifier.createNotification
import utils.attempt.FailedAttempt

object IamAudit {
  def makeCredentialsNotification(allCreds: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]]):List[Either[FailedAttempt, Notification]] = {
    allCreds.toList.map { case (awsAccount, eFCreds) =>
      eFCreds.map { credsReport =>
        val outdatedKeys = outdatedKeysInfo(findOldAccessKeys(credsReport))
        val missingMfa = missingMfaInfo(findMissingMfa(credsReport))
        val message = createMessage(outdatedKeys, missingMfa)
        createNotification(Account(awsAccount.id), message)
      }
    }
  }

  def findOldAccessKeys(credsReport: CredentialReportDisplay): CredentialReportDisplay = {
    val filteredMachines = credsReport.machineUsers.filter(user => hasOutdatedKey(List(user.key1, user.key2)))
    val filteredHumans = credsReport.humanUsers.filter(user => hasOutdatedKey(List(user.key1, user.key2)))
    credsReport.copy(machineUsers = filteredMachines, humanUsers = filteredHumans)
  }

  def hasOutdatedKey(keys: List[AccessKey]): Boolean = keys.exists(key => DateUtils.dayDiff(key.lastRotated).getOrElse(1L) > 90L)

  def findMissingMfa(credsReport: CredentialReportDisplay): CredentialReportDisplay = {
    val filteredMachines = credsReport.machineUsers.filterNot(_.username == "")
    val filteredHumans = credsReport.humanUsers.filterNot(_.hasMFA)
    credsReport.copy(machineUsers = filteredMachines, humanUsers = filteredHumans)
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
