package schedule

import com.gu.anghammarad.models.{Notification, Target, AwsAccount => Account}
import config.Config.{iamAlertCadence, iamHumanUserRotationCadence, iamMachineUserRotationCadence}
import logic.DateUtils
import model._
import org.joda.time.{DateTime, Days}
import play.api.Logging
import schedule.IamMessages._
import schedule.IamNotifier.notification
import utils.attempt.FailedAttempt

object IamAudit extends Logging {

  def makeNotification(flaggedCredsToNotify: Map[AwsAccount, Seq[IAMAlertTargetGroup]]): List[IamNotification] = {
    flaggedCredsToNotify.toList.flatMap { case (awsAccount, targetGroups) =>
      if (targetGroups.isEmpty) {
        logger.info(s"found no IAM user issues for ${awsAccount.name}. No notification required.")
        None
      } else {
        targetGroups.map(tg => createWarningAndFinalNotification(tg, awsAccount, createIamAuditUsers(tg.users, awsAccount)))
      }
    }
  }

  def createIamAuditUsers(users: Seq[VulnerableUser], account: AwsAccount): Seq[IamAuditUser] = {
    users.map { user =>
      IamAuditUser(
        Dynamo.createId(account, user.username), account.name, user.username,
        List(IamAuditAlert(DateTime.now, createDeadlineIfMissing(user.disableDeadline)))
      )
    }
  }

  def createWarningAndFinalNotification(tg: IAMAlertTargetGroup, awsAccount: AwsAccount, users: Seq[IamAuditUser]): IamNotification = {
    logger.info(s"for ${awsAccount.name}, generating iam notification message for ${tg.users.length} user(s) with outdated keys and and/or missing mfa")
    val (usersToReceiveWarningAlerts, usersToReceiveFinalAlerts) = sortUsersIntoWarningOrFinalAlerts(tg.users)

    val warningNotifications = {
      if (usersToReceiveWarningAlerts.nonEmpty)
        Some(createNotification(warning = true, usersToReceiveWarningAlerts, awsAccount, tg.targets))
      else None
    }

    val finalNotifications =
      if (usersToReceiveFinalAlerts.nonEmpty)
        Some(createNotification(warning = false, usersToReceiveFinalAlerts, awsAccount, tg.targets))
      else None

    IamNotification(warningNotifications, finalNotifications, users)
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
        // alert the Ophan AWS account using tags to ensure that the Ophan and Data Tech teams who share the same AWS account receive the right emails
        if (awsAccount.name == "Ophan") (awsAccount, Right(getTargetGroups(report, awsAccount, dynamo)))
        else (awsAccount, Right(Seq(IAMAlertTargetGroup(List.empty, getUsersToAlert(report, awsAccount, dynamo)))))
      }
    }.collect { case (awsAccount, Right(report)) => (awsAccount, report) }
  }

  private def sortUsersIntoWarningOrFinalAlerts(users: Seq[VulnerableUser]): (Seq[VulnerableUser], Seq[VulnerableUser]) = {
    val warningAlerts = users.filter(user => isWarningAlert(createDeadlineIfMissing(user.disableDeadline)))
    val finalAlerts = users.filter(user => isFinalAlert(createDeadlineIfMissing(user.disableDeadline)))
    (warningAlerts, finalAlerts)
  }

  private def createNotification(warning: Boolean, users: Seq[VulnerableUser], awsAccount: AwsAccount, targets: List[Target]) = {
    val subject = if (warning) warningSubject(awsAccount) else finalSubject(awsAccount)
    val message = if (warning) createWarningMessage(awsAccount, users) else createFinalMessage(awsAccount, users)
    notification(subject, message, targets :+ Account(awsAccount.accountNumber))
  }

  private def createDeadlineIfMissing(date: Option[DateTime]): DateTime = date.getOrElse(DateTime.now.plusDays(iamAlertCadence))

  def isWarningAlert(deadline: DateTime, today: DateTime = DateTime.now): Boolean = {
    deadline.withTimeAtStartOfDay == today.withTimeAtStartOfDay.plusWeeks(1) ||
      deadline.withTimeAtStartOfDay == today.withTimeAtStartOfDay.plusWeeks(3)
  }
  def isFinalAlert(deadline: DateTime, today: DateTime = DateTime.now): Boolean = deadline.withTimeAtStartOfDay == today.withTimeAtStartOfDay.plusDays(1)

  private def getUsersToAlert(report: CredentialReportDisplay, awsAccount: AwsAccount, dynamo: Dynamo): Seq[VulnerableUser] = {
    val vulnerableUsers = findVulnerableUsers(report)
    filterUsersToAlert(vulnerableUsers, awsAccount, dynamo)
  }

  private def getTargetGroups(report: CredentialReportDisplay, awsAccount: AwsAccount, dynamo: Dynamo): Seq[IAMAlertTargetGroup] = {
    getNotificationTargetGroups(getUsersToAlert(report, awsAccount, dynamo))
  }

  private def findVulnerableUsers(report: CredentialReportDisplay): Seq[VulnerableUser] = {
    outdatedKeysInfo(findOldAccessKeys(report)) ++ missingMfaInfo(findMissingMfa(report))
  }

  // if the user is not present in dynamo, that means they've never been alerted before, so mark them as ready to be alerted
  private def filterUsersToAlert(users: Seq[VulnerableUser], awsAccount: AwsAccount, dynamo: Dynamo): Seq[VulnerableUser] = {
    val usersWithDeadline = enrichUsersWithDeadline(users, awsAccount, dynamo)
    usersWithDeadline.filter { user =>
      user.disableDeadline.exists(deadline => isWarningAlert(deadline) || isFinalAlert(deadline)) || user.disableDeadline.isEmpty
    }
  }

  // adds deadline to users when this field is present in dynamoDB
  private def enrichUsersWithDeadline(users: Seq[VulnerableUser], awsAccount: AwsAccount, dynamo: Dynamo): Seq[VulnerableUser] = {
    users.map { user =>
      dynamo.getAlert(awsAccount, user.username).map { u =>
        user.copy(user.username, user.tags, Some(getNearestDeadline(u.alerts)))
      }.getOrElse(user)
    }
  }

  def getNearestDeadline(alerts: List[IamAuditAlert], today: DateTime = DateTime.now): DateTime = {
    val (nearestDeadline, _) = alerts.foldRight[(DateTime, Int)]((DateTime.now, iamAlertCadence)){ case (alert, (acc, startingNumberOfDays)) =>
      val daysBetweenTodayAndDeadline: Int = Days.daysBetween(today, alert.disableDeadline).getDays
      if (daysBetweenTodayAndDeadline < startingNumberOfDays) (alert.disableDeadline, daysBetweenTodayAndDeadline) else (acc, startingNumberOfDays)
    }
    nearestDeadline
  }

  /**
    * Takes users with outdated keys/missing mfa and groups them based off the stack/stage/app tags of the users.
    * Produce a group containing the two groups of users (outdated keys/missing mfa) and a list of Anghammarad Targets
    * for alerts about those users to be sent to
    * @param vulnerableUsers
    * @return
    */
  def getNotificationTargetGroups(vulnerableUsers: Seq[VulnerableUser]): Seq[IAMAlertTargetGroup] = {
    val ssaStrings = vulnerableUsers.map(k => Tag.tagsToSSAID(k.tags)).distinct
    val groups = vulnerableUsers.groupBy(u => Tag.tagsToSSAID(u.tags))

    // merge groups into IAMAlertTargetGroup seq
    ssaStrings.map { ssaString =>
      val users =  groups.getOrElse(ssaString, Seq())

      // assume that within a group all tags are the same. Use first element of the group to generate tags
      val targets = users.headOption.map(k => Tag.tagsToAnghammaradTargets(k.tags)).getOrElse(List())

      IAMAlertTargetGroup(targets, users)
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
