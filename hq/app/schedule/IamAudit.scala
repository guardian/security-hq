package schedule

import com.gu.anghammarad.models.{Notification, AwsAccount => Account}
import config.Config.{iamHumanUserRotationCadence, iamMachineUserRotationCadence}
import logic.DateUtils
import model._
import schedule.IamMessages.createMessage
import schedule.IamNotifier.createNotification
import utils.attempt.FailedAttempt

object IamAudit {
  /**
    * Takes users with outdated keys/missing mfa and groups them based off the stack/stage/app tags of the users.
    * Produce a group containing the two groups of users (outdated keys/missing mfa) and a list of Anghammarad Targets
    * for alerts about those users to be sent to
    * @param outdatedKeys
    * @param missingMfa
    * @return
    */
  def getNotificationTargetGroups(outdatedKeys: Seq[UserWithOutdatedKeys], missingMfa: Seq[UserNoMfa]): Seq[IAMAlertTargetGroup] = {
    val outdatedKeysGroups = outdatedKeys.groupBy(u => Tag.tagsToSSAID(u.tags))
    val missingMfaGroups = missingMfa.groupBy(u => Tag.tagsToSSAID(u.tags))

    // merge groups into IAMAlertTargetGroup seq
    outdatedKeysGroups.toSeq.map {
      case (ssaString, user) =>
        // assume that within a group all tags are the same. Use first element of the group to generate tags
        val targets = user.headOption.map(k => Tag.tagsToAnghammaradTargets(k.tags)).getOrElse(List())
        // check missingMfaGroup for users with matching tags
        val missingMfaUsers = missingMfaGroups.getOrElse(ssaString, Seq())
        IAMAlertTargetGroup(targets, user, missingMfaUsers)
    }
  }

  def makeCredentialsNotification(allCreds: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]]):List[Either[FailedAttempt, Seq[Notification]]] = {
    allCreds.toList.map { case (awsAccount, eFCreds) =>
      eFCreds.map { credsReport =>
        val outdatedKeys = outdatedKeysInfo(findOldAccessKeys(credsReport))
        val missingMfa = missingMfaInfo(findMissingMfa(credsReport))

        val targetGroups = getNotificationTargetGroups(outdatedKeys, missingMfa)
        targetGroups.map { tg =>
          val message = createMessage(tg.outdatedKeysUsers, tg.noMfaUsers)
          createNotification(tg.targets :+ Account(awsAccount.id), message)
        }
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
