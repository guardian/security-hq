package schedule

import aws.AwsClient
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import config.Config.{iamHumanUserRotationCadence, iamMachineUserRotationCadence}
import model.{AwsAccount, VulnerableUser}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

object IamMessages {

  val sourceSystem = "Security HQ Credentials Notifier"

  def warningSubject(account: AwsAccount): String = s"Action ${account.name}: Insecure credentials"
  def finalSubject(account: AwsAccount): String = s"Action ${account.name}: Deactivating credentials tomorrow"
  def passwordRemovedSubject(user: VulnerableUser, client: AwsClient[AmazonIdentityManagementAsync]): String =
    s"PASSWORD REMOVED for AWS IAM User ${user.username} in ${client.account.name}"
  def disabledAccessKeySubject(username: String, account: AwsAccount): String = s"ACCESS KEY DISABLED for AWS IAM User $username in ${account.name}"

  def passwordRemovedMessage(user: VulnerableUser): String = {
    s"""
       |The Permanent IAM user ${user.username}'s password has been removed today by Security HQ (https://security-hq.gutools.co.uk/), because multi factor authentication (mfa) was not enabled.
       |This can be remediated by creating a new password (https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_passwords.html) and turning on mfa (https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_mfa.html).
       |If you have any questions, please contact the Developer Experience team: devx@theguardian.com.
       |""".stripMargin
  }

  def disabledAccessKeyMessage(username: String, accessKeyId: String): String = {
    s"""
       |The Permanent IAM user $username's access key (id: $accessKeyId) was disabled today by Security HQ (https://security-hq.gutools.co.uk/), because it had not been rotated.
       |This can be remediated by creating a new access key (https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html#Using_RotateAccessKey).
       |Please remember to rotate human user access keys within ${iamHumanUserRotationCadence.toString} days or ${iamMachineUserRotationCadence.toString} days for machines.
       |If you have any questions, please contact the Developer Experience team: devx@theguardian.com.
       |""".stripMargin
  }

  def message(account: AwsAccount) = {
    s"Please check the following permanent credentials in AWS Account ${account.name}/${account.accountNumber}, which have been flagged as either needing to be rotated or requiring multi-factor authentication (if you're already planning on doing this, please ignore this message). If this is not rectified before the deadline, Security HQ will automatically disable this user:"
  }
  def createWarningMessage(account: AwsAccount, users: Seq[VulnerableUser]): String = {
    s"""
       |${message(account)}
       |${users.map(printFormat).mkString("\n")}
       |$boilerPlateText
       |""".stripMargin
  }

  def createFinalMessage(account: AwsAccount, users: Seq[VulnerableUser]): String = {
    s"""
       |${message(account)}
       |${users.map(printFormat).mkString("\n")}
       |$boilerPlateText
       |""".stripMargin
  }

  private def printFormat(user: VulnerableUser): String = {
    s"""
       |Username: ${user.username}
       |If this is not rectified by ${dateTimeToString(user.disableDeadline)}, the user will be disabled.
       |""".stripMargin
  }

  val boilerPlateText = List(
    "----------------------------------------------------------------------------------",
    "",
    "To see why these keys have been flagged and how to rectify this, see Security HQ (https://security-hq.gutools.co.uk/iam).",
    "",
    "Here is some helpful documentation on:",
    "",
    "rotating credentials: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html,",
    "",
    "deleting users: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_users_manage.html#id_users_deleting_console,",
    "",
    "multi-factor authentication: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_mfa.html.",
    "",
    "If you have any questions, please contact the Developer Experience team: devx@theguardian.com."
  ).mkString("\n")

  private def dateTimeToString(day: Option[DateTime]): String = {
    val dateTimeFormatPattern = DateTimeFormat.forPattern("dd/MM/yyyy")
    day match {
      case Some(date) =>  dateTimeFormatPattern.print(date)
      case None => "Unknown"
    }
  }
}

