package notifications

import com.gu.anghammarad.Anghammarad
import com.gu.anghammarad.models.{Email, Notification, Preferred, Target, AwsAccount as Account}
import com.typesafe.scalalogging.LazyLogging
import config.CoreConfig.{daysBetweenFinalNotificationAndRemediation, daysBetweenWarningAndFinalNotification}
import logic.DateUtils.printDay
import model.{AwsAccount, CredentialMetadata, IAMUser, Tag}
import org.joda.time.DateTime
import software.amazon.awssdk.services.sns.SnsAsyncClient
import utils.attempt.{Attempt, Failure}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

object AnghammaradNotifications extends LazyLogging {
  def send(
      notification: Notification,
      topicArn: String,
      snsClient: SnsAsyncClient
  )(using ExecutionContext): Attempt[String] = {
    Attempt
      .fromFuture(Anghammarad.notify(notification, topicArn, snsClient)) { case NonFatal(e) =>
        Failure(
          s"Failed to send Anghammarad notification ${e.getMessage}",
          throwable = Some(e)
        ).attempt
      }
      .tap {
        case Left(failure) =>
          logger.error(failure.logMessage, failure.firstException.orNull)
        case Right(id) =>
          logger.info(s"Sent notification to ${notification.target}: $id")
      }
  }

  private val channel = Preferred(Email)
  private val sourceSystem = "Security HQ Credentials Notifier"

  private def notificationTargets(awsAccount: AwsAccount, iamUser: IAMUser): List[Target] =
    Tag.tagsToAnghammaradTargets(iamUser.tags) :+ Account(awsAccount.accountNumber)

  // The console shows you an option to add a description for a key. This is implemented as a tag
  // on the IAM user, which has the same Key as the Access Key ID.
  private def keyDescription(iamUser: IAMUser, key: CredentialMetadata): String =
    iamUser.tags.find(_.key == key.accessKeyId).map(_.value) match {
      case Some(description) => s"This access key has description: $description"
      case None              => "This access key has no description set."
    }

  // All of the tags on the User which are *not* added as descriptions for individual keys.
  private def userTags(iamUser: IAMUser) = iamUser.tags
    .filterNot(_.key.startsWith("AKIA")) // Common prefix for IAM user static credentials
    .map(t => s"${t.key}=${t.value}")
    .mkString(", ")

  private def credentialInfo(
      awsAccount: AwsAccount,
      iamUser: IAMUser,
      credentialThatWillBeDisabled: CredentialMetadata
  ): String =
    s"""Further info:
       |This is the access key which is affected: ${credentialThatWillBeDisabled.accessKeyId}
       |${keyDescription(iamUser, credentialThatWillBeDisabled)}
       |This user's tags:
       |  ${userTags(iamUser)}
       |Follow this link to find the user in the console: (remember you'll need to sign into the ${awsAccount.name} account first via Janus)
       |https://us-east-1.console.aws.amazon.com/iam/home?region=us-east-1#/users/details/${iamUser.username}?section=security_credentials
       |""".stripMargin

  private def outdatedCredentialWarningMessage(
      awsAccount: AwsAccount,
      iamUser: IAMUser,
      problemCreationDate: String,
      credentialThatWillBeDisabled: CredentialMetadata,
      deadline: String
  ): String =
    s"""Please rotate the permanent credential for ${iamUser.username} in AWS Account ${awsAccount.name},
       |which has been flagged because it was last rotated on ${problemCreationDate}.
       |
       |${credentialInfo(awsAccount, iamUser, credentialThatWillBeDisabled)}
       |
       |If no action is taken before ${deadline}, this credential will be automatically disabled
       |on or shortly after that date, which will likely break any applications still using it!
       |
       |$genericCredentialWarningText
       |""".stripMargin

  def outdatedCredentialWarning(
      awsAccount: AwsAccount,
      iamUser: IAMUser,
      problemCreationDate: DateTime,
      credentialThatWillBeDisabled: CredentialMetadata,
      now: DateTime
  ): Notification = {
    val deadline = printDay(
      now.plusDays(daysBetweenWarningAndFinalNotification + daysBetweenFinalNotificationAndRemediation)
    )
    val message = outdatedCredentialWarningMessage(
      awsAccount,
      iamUser,
      printDay(problemCreationDate),
      credentialThatWillBeDisabled,
      deadline
    )
    val subject = s"Action required by $deadline: long-lived credential detected in ${awsAccount.name}"
    Notification(
      subject,
      message,
      Nil,
      notificationTargets(awsAccount, iamUser),
      channel,
      sourceSystem
    )
  }

  def outdatedCredentialFinalWarning(
      awsAccount: AwsAccount,
      iamUser: IAMUser,
      problemCreationDate: DateTime,
      credentialThatWillBeDisabled: CredentialMetadata,
      now: DateTime
  ): Notification = {
    val deadline = printDay(now.plusDays(daysBetweenFinalNotificationAndRemediation))
    val message = outdatedCredentialWarningMessage(
      awsAccount,
      iamUser,
      printDay(problemCreationDate),
      credentialThatWillBeDisabled,
      deadline
    )
    val subject = s"Action required by $deadline: long-lived credential in ${awsAccount.name} will be disabled soon"
    Notification(
      subject,
      message,
      Nil,
      notificationTargets(awsAccount, iamUser),
      channel,
      sourceSystem
    )
  }

  def outdatedCredentialRemediation(
      awsAccount: AwsAccount,
      iamUser: IAMUser,
      problemCreationDate: DateTime,
      credentialThatWasDisabled: CredentialMetadata
  ): Notification = {
    val message =
      s"""A permanent credential for ${iamUser.username} in AWS Account ${awsAccount.name} was disabled today,
         |because it was last rotated on ${printDay(problemCreationDate)}.
         |
         |${credentialInfo(awsAccount, iamUser, credentialThatWasDisabled)}
         |
         |If any applications were still relying on this credential, they have likely been broken!
         |If you still require access using this user, you should create a new credential and rotate regularly.
         |Otherwise, please delete the ${iamUser.username} IAM user.
         |
         |$genericCredentialWarningText
         |""".stripMargin
    val subject = s"DISABLED long-lived credential in ${awsAccount.name}"
    Notification(
      subject,
      message,
      Nil,
      notificationTargets(awsAccount, iamUser),
      channel,
      sourceSystem
    )
  }

  def outdatedCredentialNoRemediationDevXSecurity(
      awsAccount: AwsAccount,
      iamUser: IAMUser,
      problemCreationDate: DateTime,
      devXSecurityAccount: AwsAccount,
      credentialThatWillBeDisabled: CredentialMetadata
  ): Notification = {
    val endUserNotificationTargets = notificationTargets(awsAccount, iamUser)
    val devxSecurityNotificationTargets = List(Account(devXSecurityAccount.accountNumber))
    val endUserTargetsString = endUserNotificationTargets.map(_.toString).mkString(", ")
    val message =
      s"""A permanent credential for ${iamUser.username} in ${awsAccount.name} was eligible for deactivation today,
         |because it was last rotated on ${printDay(problemCreationDate)}.
         |
         |It wasn't deactivated because it's not Deactivation Tuesday.
         |
         |The end users are $endUserTargetsString
         |
         |THIS ACTION HAS HIGH POTENTIAL TO BREAK THINGS.
         |
         |BE PREPARED FOR USERS TO BE UPSET!
         |
         |${credentialInfo(awsAccount, iamUser, credentialThatWillBeDisabled)}
         |""".stripMargin
    val subject = s"Imminent disabling of long-lived credential in ${awsAccount.name}"
    Notification(
      subject,
      message,
      Nil,
      devxSecurityNotificationTargets,
      channel,
      sourceSystem
    )
  }

  def outdatedCredentialRemediationDevXSecurity(
      awsAccount: AwsAccount,
      iamUser: IAMUser,
      problemCreationDate: DateTime,
      devXSecurityAccount: AwsAccount,
      credentialThatWillBeDisabled: CredentialMetadata
  ): Notification = {
    val endUserNotificationTargets = notificationTargets(awsAccount, iamUser)
    val devxSecurityNotificationTargets = List(Account(devXSecurityAccount.accountNumber))
    val endUserTargetsString = endUserNotificationTargets.map(_.toString).mkString(", ")
    val message =
      s"""
         |The permanent credential, ${iamUser.username}, in ${awsAccount.name} was disabled today,
         |because it was last rotated on ${printDay(problemCreationDate)}.
         |
         |Notification(s) have been sent to $endUserTargetsString, who are the owners of the disabled credential.
         |
         |THIS ACTION HAS HIGH POTENTIAL TO BREAK THINGS.
         |
         |BE PREPARED FOR USERS TO BE UPSET!
         |
         |${credentialInfo(awsAccount, iamUser, credentialThatWillBeDisabled)}
         |""".stripMargin
    val subject = s"DISABLED long-lived credential in ${awsAccount.name}"
    Notification(
      subject,
      message,
      Nil,
      devxSecurityNotificationTargets,
      channel,
      sourceSystem
    )
  }

  private val genericCredentialWarningText =
    s"""If you're not sure what this warning means, would like help resolving or have any questions, please contact the Developer Experience team: devx@theguardian.com.
       |
       |To see more details on the status of this or any other credential, see this dashboard: https://metrics.gutools.co.uk/d/bdn97cui5rbi8f/iam-credentials-report?orgId=1.
       |Here is some helpful documentation on:
       |rotating credentials: https://docs.aws.amazon.com/IAM/latest/UserGuide/id-credentials-access-keys-update.html#rotating_access_keys_console
       |deleting users: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_users_remove.html
       |""".stripMargin

  def unrecognisedUserRemediation(awsAccount: AwsAccount, iamUser: IAMUser): Notification = {
    val message =
      s"""A permanent credential for ${iamUser.username}, in ${awsAccount.name} was disabled today.
         |This is because it was identified as belonging to a person who does not have an entry in Janus.
         |
         |If you still require the disabled user, please ensure they are tagged correctly with their Google username
         |and have an entry in Janus.
         |If the disabled user has left the organisation, this IAM user should be deleted.
         |
         |$genericCredentialWarningText
         |""".stripMargin
    val subject = s"AWS IAM User ${iamUser.username} DISABLED in ${awsAccount.name} Account"
    Notification(subject, message, Nil, notificationTargets(awsAccount, iamUser), channel, sourceSystem)
  }
}
