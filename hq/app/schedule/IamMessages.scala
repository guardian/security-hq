package schedule

import model.{AwsAccount, UserNoMfa, UserWithOutdatedKeys}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

object IamMessages {
  def subject(account: AwsAccount) = s"Action required: Insecure credentials in the ${account.name} AWS Account scheduled for deactivaton"
  val sourceSystem = "Security HQ Credentials Notifier"
  def outdatedKeysMessage(account: AwsAccount) =
    s"Please rotate the following IAM access keys in AWS Account ${account.name}/${account.accountNumber} or delete them if they are disabled and unused (if you're already planning to do this, please ignore this message). If this is not rectified before the disable deadline, Security HQ will automatically disable this user:"
  def missingMfaMessage(account: AwsAccount) =
    s"Please add multi-factor authentication to the following AWS IAM users in Account ${account.name}/${account.accountNumber}. If this is not rectified before the disable deadline, Security HQ will automatically disable this user:"
  val boilerPlateText = List(
    "Here is some helpful documentation on:",
    "",
    "rotating credentials: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html,",
    "",
    "deleting users: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_users_manage.html#id_users_deleting_console,",
    "",
    "multi-factor authentication: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_mfa.html.",
    "",
    "For an overview of security vulnerabilities in your AWS account, see Security HQ (https://security-hq.gutools.co.uk/).",
    "If you have any questions, please contact the Developer Experience team: devx@theguardian.com."
  ).mkString("\n")

  def createMessage(outdatedKeys: Seq[UserWithOutdatedKeys], missingMfa: Seq[UserNoMfa], account: AwsAccount): String = {
    if (outdatedKeys.isEmpty)
      s"""
         |${missingMfaMessage(account)}
         |${missingMfa.map(printFormatMissingMfa).mkString("\n")}
         |$boilerPlateText
         |""".stripMargin
    else if (missingMfa.isEmpty)
    s"""
       |${outdatedKeysMessage(account)}
       |${outdatedKeys.map(printFormatOutdatedKeys).mkString("\n")}
       |$boilerPlateText
       |""".stripMargin
    else
      s"""
         |${outdatedKeysMessage(account)}
         |${outdatedKeys.map(printFormatOutdatedKeys).mkString("\n")}
         |${missingMfaMessage(account)}
         |${missingMfa.map(printFormatMissingMfa).mkString("\n")}
         |$boilerPlateText
         |""".stripMargin
  }
  private def printFormatOutdatedKeys(user: UserWithOutdatedKeys): String = {
    s"""
      |Username: ${user.username}
      |Key 1 last rotation: ${dateTimeToString(user.key1LastRotation)}
      |Key 2 last rotation: ${dateTimeToString(user.key2LastRotation)}
      |Last active: ${dateToString(user.userLastActiveDay)}
      |If this is not rectified by ${dateTimeToString(user.disableDeadline)}, the user will be disabled.
      |""".stripMargin
  }
  private def printFormatMissingMfa(user: UserNoMfa): String = {
    s"""
      |Username: ${user.username}
      |Last active: ${dateToString(user.userLastActiveDay)}
      |If this is not rectified by ${dateTimeToString(user.disableDeadline)}, the user will be disabled.
      |""".stripMargin
  }
  private def dateToString(day: Option[Long]): String = day match {
    case Some(0) => "Today"
    case Some(1) => "Yesterday"
    case Some(d) => s"${d.toString} days ago"
    case _ => "Unknown"
  }
  private def dateTimeToString(day: Option[DateTime]): String = {
    val dateTimeFormatPattern = DateTimeFormat.forPattern("dd/MM/yyyy")
    day match {
      case Some(date) =>  dateTimeFormatPattern.print(date)
      case None => "Unknown"
    }
  }
}

