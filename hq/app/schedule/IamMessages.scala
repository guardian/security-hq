package schedule

import model.{UserNoMfa, UserWithOutdatedKeys}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

object IamMessages {
  val subject = "Action required - old AWS credentials and/or credentials missing MFA"
  val sourceSystem = "Security HQ Credentials Notifier"
  val outdatedKeysMessage: String = "Please rotate the following AWS IAM access keys or delete them if they are disabled and unused:"
  val missingMfaMessage: String = "Please add multi-factor authentication to the following AWS IAM users:"
  val boilerPlateText: String =
    """
      |Documentation on rotating credentials: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html.
      |Documentation on deleting users: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_users_manage.html#id_users_deleting_console.
      |Documentation on multi-factor authentication: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_mfa.html.
      |For an overview of security vulnerabilities in your AWS account, see Security HQ (https://security-hq.gutools.co.uk/).
      |If you have any questions, please contact the Developer Experience team: devx@theguardian.com.
      |""".stripMargin

  def createMessage(outdatedKeys: Seq[UserWithOutdatedKeys], missingMfa: Seq[UserNoMfa]): String = {
    if (outdatedKeys.isEmpty)
      s"""
         |$missingMfaMessage
         |${missingMfa.map(printFormatMissingMfa).mkString("\n")}
         |$boilerPlateText
         |""".stripMargin
    else if (missingMfa.isEmpty)
    s"""
       |$outdatedKeysMessage
       |${outdatedKeys.map(printFormatOutdatedKeys).mkString("\n")}
       |$boilerPlateText
       |""".stripMargin
    else
      s"""
         |$outdatedKeysMessage
         |${outdatedKeys.map(printFormatOutdatedKeys).mkString("\n")}
         |$missingMfaMessage
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
      |""".stripMargin
  }
  private def printFormatMissingMfa(user: UserNoMfa): String = {
    s"""
      |Username: ${user.username}
      |Last active: ${dateToString(user.userLastActiveDay)}
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

