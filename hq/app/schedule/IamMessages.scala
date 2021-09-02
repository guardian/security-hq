package schedule

import model.{AwsAccount, VulnerableUser}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

object IamMessages {

  object FormerStaff {
    private def message(account: AwsAccount): String = {
      s"Please check the following permanent human credentials in AWS Account ${account.name}, " +
      s"which have been flagged as not being recognised as current staff members. If you believe these user(s) " +
      s"have left the organisation then no action is necessary and they will be automatically removed. If they belong to" +
      s"current employees then please ensure the IAM user is tagged [appropriately] and they appear in Janus [in the right place] "
      //TODO: finish the message once we decide on method for tagging and a source for this data
    }

    private val boilerPlateText = List(
      "----------------------------------------------------------------------------------",
      "",
      "Here is some helpful documentation on:",
      "",
      "IAM user best practice including setting up human IAM users: https://security-hq.gutools.co.uk/documentation/[best practice],", //TODO: add location of docs
      "",
      "If you have any questions, please contact the Developer Experience team: devx@theguardian.com."
    ).mkString("\n")

    def warningSubject(account: AwsAccount): String = s"Action ${account.name}: IAM credentials belonging to unrecognised user"

    def createWarningMessage(account: AwsAccount, users: Seq[VulnerableUser]): String = {
      s"""
         |${message(account)}
         |${users.map(printFormat).mkString("\n")}
         |$boilerPlateText
         |""".stripMargin
    }
  }

  object InsecureCredentials {
    private def message(account: AwsAccount): String = {
      s"Please check the following permanent credentials in AWS Account ${account.name}, " +
      s"which have been flagged as either needing to be rotated or requiring multi-factor authentication (if you're " +
      s"already planning on doing this, please ignore this message). If this is not rectified before the deadline, " +
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
      "multi-factor authentication: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_mfa.html.",
      "",
      "If you have any questions, please contact the Developer Experience team: devx@theguardian.com."
    ).mkString("\n")

    def warningSubject(account: AwsAccount): String = s"Action ${account.name}: Insecure credentials"
    def finalSubject(account: AwsAccount): String = s"Action ${account.name}: Deactivating credentials tomorrow"
    def disabledUsersSubject(account: AwsAccount): String = s"AWS IAM User(s) DISABLED in ${account.name} Account"

    def disabledUsersMessage(users: Seq[VulnerableUser]): String = {
      s"""
         |The following Permanent IAM user(s) have been disabled today: ${users.map(_.username).mkString(",")}.
         |Please check Security HQ to review the IAM users in your account (https://security-hq.gutools.co.uk/iam).
         |If you still require the disabled user, add new access keys(s) and rotate regularly going forwards, or add MFA for human users.
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

