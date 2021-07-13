package schedule

import model.{AwsAccount, VulnerableUser}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

object IamMessages {

  val sourceSystem = "Security HQ Credentials Notifier"

  def warningSubject(account: AwsAccount): String = s"Action ${account.name}: Insecure credentials"
  def finalSubject(account: AwsAccount): String = s"Action ${account.name}: Deactivating credentials tomorrow"

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

