package schedule

import model.{AwsAccount, VulnerableUser}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

object IamMessages {

  object FormerStaff {
    def disabledUsersMessage(users: Seq[VulnerableUser]): String = {
      s"""
         |The following Permanent IAM user(s) have been disabled today: ${users.map(_.username).mkString(",")}.
         |Please check Security HQ to review the IAM users in your account (https://security-hq.gutools.co.uk/iam).
         |If you still require the disabled user, ensure they are tagged correctly with their Google username
         |and have an entry in Janus.
         |If the disabled user has left the organisation, they should be deleted.
         |If you have any questions, contact devx@theguardian.com.
         |""".stripMargin
    }
  }

  object VulnerableCredentials {
    private def message(account: AwsAccount): String = {
      s"Please check the following permanent credentials in AWS Account ${account.name}, " +
      s"which have been flagged as needing to be rotated (if you're already planning on doing this," +
      s"please ignore this message). If this is not rectified before the deadline, " +
      s"Security HQ will automatically disable this user:"
    }

    private val boilerPlateText = List(
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
      "If you have any questions, please contact the Developer Experience team: devx@theguardian.com."
    ).mkString("\n")

    def warningSubject(account: AwsAccount): String = s"Action ${account.name}: Vulnerable credentials"
    def finalSubject(account: AwsAccount): String = s"Action ${account.name}: Deactivating credentials tomorrow"

    def disabledUsersMessage(users: Seq[VulnerableUser]): String = {
      s"""
         |The following Permanent IAM user(s) have been disabled today: ${users.map(_.username).mkString(",")}.
         |Please check Security HQ to review the IAM users in your account (https://security-hq.gutools.co.uk/iam).
         |If you still require the disabled user, add new access keys(s) and rotate regularly going forwards.
         |If you no longer require the disabled user, they should be deleted.
         |If you have any questions, contact devx@theguardian.com.
         |""".stripMargin
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
  }

  val sourceSystem = "Security HQ Credentials Notifier"

  def disabledUsersSubject(account: AwsAccount): String = s"AWS IAM User(s) DISABLED in ${account.name} Account"

  private def printFormat(user: VulnerableUser): String = {
    s"""
       |Username: ${user.username}.
       |If this is not rectified by ${dateTimeToString(user.disableDeadline)}, the user will be disabled.
       |""".stripMargin
  }

  private def dateTimeToString(day: Option[DateTime]): String = {
    val dateTimeFormatPattern = DateTimeFormat.forPattern("dd/MM/yyyy")
    day match {
      case Some(date) =>  dateTimeFormatPattern.print(date)
      case None => "Unknown"
    }
  }
}

