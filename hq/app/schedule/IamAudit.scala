package schedule

import com.gu.anghammarad.models.{AwsAccount => Account}
import config.Config.{iamHumanUserRotationCadence, iamMachineUserRotationCadence}
import logic.DateUtils
import model._
import org.joda.time.{DateTime, Days}
import play.api.Logging
import schedule.IamMessages.createMessage
import schedule.IamNotifier.createNotification
import utils.attempt.FailedAttempt

object IamAudit extends Logging {
  /**
    * Takes users with outdated keys/missing mfa and groups them based off the stack/stage/app tags of the users.
    * Produce a group containing the two groups of users (outdated keys/missing mfa) and a list of Anghammarad Targets
    * for alerts about those users to be sent to
    * @param vulnerableUsers
    * @return
    */
  def getNotificationTargetGroups(vulnerableUsers: VulnerableUsers): Seq[IAMAlertTargetGroup] = {
    val ssaStrings = (vulnerableUsers.outdatedKeys.map(k => Tag.tagsToSSAID(k.tags)) ++ vulnerableUsers.noMfa.map(k => Tag.tagsToSSAID(k.tags))).distinct
    val outdatedKeysGroups = vulnerableUsers.outdatedKeys.groupBy(u => Tag.tagsToSSAID(u.tags))
    val missingMfaGroups = vulnerableUsers.noMfa.groupBy(u => Tag.tagsToSSAID(u.tags))

    // merge groups into IAMAlertTargetGroup seq
    ssaStrings.map { ssaString =>
      val outdatedKeysUsers =  outdatedKeysGroups.getOrElse(ssaString, Seq())
      val missingMfaUsers = missingMfaGroups.getOrElse(ssaString, Seq())

      // assume that within a group all tags are the same. Use first element of the group to generate tags
      val targets = (outdatedKeysUsers ++ missingMfaUsers).headOption.map(k => Tag.tagsToAnghammaradTargets(k.tags)).getOrElse(List())

      IAMAlertTargetGroup(targets, outdatedKeysUsers, missingMfaUsers)
    }
  }

  def getFlaggedCredentialsReports(allCreds: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]], dynamo: Dynamo): Map[AwsAccount, Seq[IAMAlertTargetGroup]] = {
   allCreds.map { case (awsAccount, maybeReport) => maybeReport match {
      case Left(error) =>
        error.failures.foreach { failure =>
          val errorMessage = s"failed to collect credentials report for IAM notifier: ${failure.friendlyMessage}"
          failure.throwable.fold(logger.error(errorMessage))(throwable => logger.error(errorMessage, throwable))
        }
        (awsAccount, Left(error))
      case Right(report) =>
        (awsAccount, Right(getTargetGroups(report, awsAccount, dynamo)))
      }
    }.collect { case (awsAccount, Right(report)) => (awsAccount, report) }
  }

  def getTargetGroups(report: CredentialReportDisplay, awsAccount: AwsAccount, dynamo: Dynamo): Seq[IAMAlertTargetGroup] = {
    val vulnerableUsers = findVulnerableUsers(report)
    val vulnerableUsersToAlert = getUsersNotRecentlyNotified(vulnerableUsers, awsAccount, dynamo)
    getNotificationTargetGroups(vulnerableUsersToAlert)
  }

  def findVulnerableUsers(report: CredentialReportDisplay): VulnerableUsers = {
    VulnerableUsers(outdatedKeysInfo(findOldAccessKeys(report)), missingMfaInfo(findMissingMfa(report)))
  }

  def getUsersNotRecentlyNotified(users: VulnerableUsers, awsAccount: AwsAccount, dynamo: Dynamo): VulnerableUsers = {
    val userNoMfas = users.noMfa.filter { user =>
      dynamo.getAlert(awsAccount, user.username).exists { notifiedUser =>
        !isAlreadyAlerted(notifiedUser.alerts)
      }
    }

    val usersWithOldKeys = users.outdatedKeys.filter { user =>
      dynamo.getAlert(awsAccount, user.username).exists { notifiedUser =>
        !isAlreadyAlerted(notifiedUser.alerts)
      }
    }

    VulnerableUsers(usersWithOldKeys, userNoMfas)
  }

  def isAlreadyAlerted(alerts: List[IamAuditAlert]): Boolean = {
    val daysSinceLastAlert = 21
    alerts.exists { alert =>
      Days.daysBetween(alert.dateNotificationSent.toLocalDate, DateTime.now.toLocalDate).getDays <= daysSinceLastAlert
    }
  }

  def makeIamNotification(flaggedCreds: Map[AwsAccount, Seq[IAMAlertTargetGroup]]): List[IamNotification] = {
    flaggedCreds.toList.flatMap { case (awsAccount, targetGroups) =>
      if (targetGroups.isEmpty) {
        logger.info(s"found no IAM user issues for ${awsAccount.name}. No notification required.")
        None
      } else {
        targetGroups.map { tg =>
          logger.info(s"for ${awsAccount.name}, generating iam notification message for ${tg.outdatedKeysUsers.length} user(s) with outdated keys and ${tg.noMfaUsers.length} user(s) with missing mfa")
          val message = createMessage(tg.outdatedKeysUsers, tg.noMfaUsers, awsAccount)
          //TODO get username
          Some(createNotification(awsAccount, tg.targets :+ Account(awsAccount.accountNumber), message, ""))
        }
      }
    }.flatten
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
        user.lastActivityDay,
        user.tags
      )
    }
    val humans = outdatedKeys.humanUsers.map { user =>
      UserWithOutdatedKeys(
        user.username,
        user.key1.lastRotated,
        user.key2.lastRotated,
        user.lastActivityDay,
        user.tags
      )
    }
    machines ++ humans
  }

  def missingMfaInfo(missingMfa: CredentialReportDisplay): Seq[UserNoMfa] = {
    missingMfa.humanUsers.map { user =>
      UserNoMfa(
        user.username,
        user.lastActivityDay,
        user.tags
      )
    }
  }
}
