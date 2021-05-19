package schedule

import com.gu.anghammarad.models.Stack
import model.{UserWithOutdatedKeys, _}
import com.gu.anghammarad.models.{Email, Notification, Preferred, AwsAccount => AwsAccountTarget}
import model._
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}
import schedule.IamAudit._
import utils.attempt.FailedAttempt

class IamAuditTest extends FreeSpec with Matchers {
  val outdatedUser1: UserWithOutdatedKeys = UserWithOutdatedKeys("lesleyKnope", Some(DateTime.now.minusDays(400)), None, None, List())
  val outdatedUser2: UserWithOutdatedKeys = UserWithOutdatedKeys("ronSwanson", Some(DateTime.now.minusDays(400)), None, None, List())
  val outdatedUser3: UserWithOutdatedKeys = UserWithOutdatedKeys("tomHaverford", Some(DateTime.now.minusDays(400)), None, None, List())

  val noMfaUser1: UserNoMfa = UserNoMfa("april", None, List())
  val noMfaUser2: UserNoMfa = UserNoMfa("andy", None, List())
  val noMfaUser3: UserNoMfa = UserNoMfa("diane", None, List())


  "getNotificationTargetGroups" - {
    "correctly builds target groups when users have no tags " in {
      val targetGroups = getNotificationTargetGroups(Seq(outdatedUser1, outdatedUser2), Seq(noMfaUser1, noMfaUser2))
      targetGroups.length shouldEqual 1
      targetGroups.head.noMfaUsers.length shouldEqual 2
      targetGroups.head.outdatedKeysUsers.length shouldEqual 2
    }

    "correctly builds target groups when users have tags" in {
      val parks = Tag("Stack", "parks")
      val recreation = Tag("Stack", "recreation")

      val inputOutdatedUsers = Seq(
        outdatedUser1,
        outdatedUser2.copy(tags=List(parks)),
        outdatedUser3.copy(tags=List(recreation))
      )

      val inputNoMfaUsers = Seq(
        noMfaUser1,
        noMfaUser2.copy(tags=List(Tag("Stack", "parks"))),
        noMfaUser3.copy(tags=List(Tag("Stack", "recreation")))
      )

      val targetGroups = getNotificationTargetGroups(inputOutdatedUsers, inputNoMfaUsers)

      targetGroups.length shouldEqual 3

      val parkGroup = targetGroups.find(t => t.targets.exists{
        case Stack(stack) => stack == "parks"
        case _ => false
      })
      parkGroup shouldBe defined
      val parkGroupNoMfaUsers = parkGroup.get.noMfaUsers
      parkGroupNoMfaUsers.head.username shouldBe noMfaUser2.username

      val noTagGroup = targetGroups.find(t => t.targets.isEmpty)
      noTagGroup shouldBe defined
      noTagGroup.get.noMfaUsers shouldEqual List(noMfaUser1)

    }

    "correctly builds target list when there are only mfa users" in {
      val targetGroups = getNotificationTargetGroups(Seq(), Seq(noMfaUser1, noMfaUser2))
      targetGroups.length shouldEqual 1
      targetGroups.head.noMfaUsers.length shouldEqual 2
      targetGroups.head.outdatedKeysUsers.length shouldEqual 0
    }
  }
  "findOldCredentialsAndMissingMfas" - {
    "returns CredentialReportDisplays with access keys greater than 90 days old" in {
      val oldHumanAccessKeyEnabled: AccessKey = AccessKey(AccessKeyEnabled, Some(new DateTime(2021, 1, 15, 1, 1)))
      val oldHumanAccessKeyDisabled: AccessKey = AccessKey(AccessKeyDisabled, Some(new DateTime(2020, 9, 1, 1, 1)))
      val oldMachineAccessKeyEnabled: AccessKey = AccessKey(AccessKeyEnabled, Some(new DateTime(2019, 1, 15, 1, 1)))
      val oldMachineAccessKeyDisabled: AccessKey = AccessKey(AccessKeyDisabled, Some(new DateTime(2018, 9, 1, 1, 1)))

      val credsReport: CredentialReportDisplay =
        CredentialReportDisplay(
          new DateTime(2021, 1, 1, 1, 1),
          Seq(
            MachineUser("", oldMachineAccessKeyEnabled, AccessKey(NoKey, None), Red, None, None, List.empty),
            MachineUser("", AccessKey(AccessKeyDisabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, None, None, List.empty),
            MachineUser("", oldMachineAccessKeyDisabled, AccessKey(NoKey, None), Red, None, None, List.empty),
          ),
          Seq(
            HumanUser("", true, oldHumanAccessKeyDisabled, AccessKey(NoKey, None), Red, None, None, List.empty),
            HumanUser("", true, oldHumanAccessKeyEnabled, AccessKey(NoKey, None), Red, None, None, List.empty),
            HumanUser("", true, AccessKey(AccessKeyEnabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, None, None, List.empty),
          )
        )
      val result: CredentialReportDisplay =
        CredentialReportDisplay(
          new DateTime(2021, 1, 1, 1, 1),
          Seq(
            MachineUser("", oldMachineAccessKeyEnabled, AccessKey(NoKey, None), Red, None, None, List.empty),
            MachineUser("", oldMachineAccessKeyDisabled, AccessKey(NoKey, None), Red, None, None, List.empty),
          ),
          Seq(
            HumanUser("", true, oldHumanAccessKeyDisabled, AccessKey(NoKey, None), Red, None, None, List.empty),
            HumanUser("", true, oldHumanAccessKeyEnabled, AccessKey(NoKey, None), Red, None, None, List.empty),
          )
        )
      findOldAccessKeys(credsReport) shouldEqual result
    }
    "returns empty human user and machine user lists when there are no access keys greater than 90 days old" in {
      val credsReport: CredentialReportDisplay = CredentialReportDisplay(
        new DateTime(2021, 1, 1, 1, 1),
        Seq(
          MachineUser("", AccessKey(AccessKeyDisabled, Some(DateTime.now().minusMonths(11))), AccessKey(NoKey, None), Red, None, None, List.empty),
        ),
        Seq(
          HumanUser("", true, AccessKey(AccessKeyEnabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, None, None, List.empty),
        )
      )
      val result: CredentialReportDisplay = CredentialReportDisplay(
        new DateTime(2021, 1, 1, 1, 1),
      )
      findOldAccessKeys(credsReport) shouldEqual result
    }
    "returns a list of human users in the credential report displays with missing Mfas" in {
      val credsReport: CredentialReportDisplay = CredentialReportDisplay(
        new DateTime(2021, 1, 1, 1, 1),
        Seq(
          MachineUser("", AccessKey(AccessKeyDisabled, Some(DateTime.now().minusMonths(10))), AccessKey(NoKey, None), Red, None, None, List.empty),
        ),
        Seq(
          HumanUser("", true, AccessKey(AccessKeyEnabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, None, None, List.empty),
          HumanUser("", false, AccessKey(AccessKeyDisabled, Some(new DateTime(2020, 9, 1, 1, 1))), AccessKey(NoKey, None), Red, None, None, List.empty),
        )
      )
      val result: CredentialReportDisplay = CredentialReportDisplay(
        new DateTime(2021, 1, 1, 1, 1),
        Seq.empty,
        Seq(
          HumanUser("", false, AccessKey(AccessKeyDisabled, Some(new DateTime(2020, 9, 1, 1, 1))), AccessKey(NoKey, None), Red, None, None, List.empty),
        )
      )
      findMissingMfa(credsReport) shouldEqual result
    }
    "returns an empty human user list in the credential report displays when there are no human users with missing Mfas" in {
      val credsReport: CredentialReportDisplay = CredentialReportDisplay(
        new DateTime(2021, 1, 1, 1, 1),
        Seq(
          MachineUser("", AccessKey(AccessKeyDisabled, Some(DateTime.now().minusMonths(9))), AccessKey(NoKey, None), Red, None, None, List.empty),
        ),
        Seq(
          HumanUser("", true, AccessKey(AccessKeyEnabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, None, None, List.empty),
          HumanUser("", true, AccessKey(AccessKeyDisabled, Some(new DateTime(2020, 9, 1, 1, 1))), AccessKey(NoKey, None), Red, None, None, List.empty),
        )
      )
      val result: CredentialReportDisplay = CredentialReportDisplay(
        new DateTime(2021, 1, 1, 1, 1),
        Seq.empty,
        Seq.empty
      )
      findMissingMfa(credsReport) shouldEqual result
    }
    "makes a credentials notification with a message including both old access keys and missing mfa" in {
      val allCreds: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = Map(
        AwsAccount("", "", "", "") -> Left(FailedAttempt(List.empty)),
        AwsAccount("", "test", "", "123456789") -> Right(CredentialReportDisplay(
          new DateTime(2021, 1, 1, 1, 1),
          Seq(
            MachineUser("machine user A", AccessKey(AccessKeyDisabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, None, None, List.empty),
            MachineUser("machine user B", AccessKey(AccessKeyEnabled, Some(new DateTime(2019, 12, 12, 1, 1))), AccessKey(NoKey, None), Red, Some(243), None, List.empty),
            MachineUser("machine user C", AccessKey(NoKey, None), AccessKey(AccessKeyEnabled, Some(new DateTime(2015, 6, 5, 12, 1))), Red, Some(243), None, List.empty),
          ),
          Seq(
            HumanUser("username A", false, AccessKey(AccessKeyEnabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, Some(365), None, List.empty),
            HumanUser("username B", true, AccessKey(AccessKeyDisabled, Some(new DateTime(2020, 9, 1, 1, 1))), AccessKey(NoKey, None), Red, Some(150), None, List.empty),
          )
        ))
      )
      val notification: Notification = Notification(
        "Action required - The test AWS Account has old AWS credentials and/or credentials missing MFA",
        """
          |Please rotate the following IAM access keys in AWS Account test/123456789 or delete them if they are disabled and unused (if you're already planning to do this, please ignore this message):
          |
          |Username: machine user B
          |Key 1 last rotation: 12/12/2019
          |Key 2 last rotation: Unknown
          |Last active: 242 days ago
          |
          |
          |Username: machine user C
          |Key 1 last rotation: Unknown
          |Key 2 last rotation: 05/06/2015
          |Last active: 242 days ago
          |
          |
          |Username: username B
          |Key 1 last rotation: 01/09/2020
          |Key 2 last rotation: Unknown
          |Last active: 150 days ago
          |
          |Please add multi-factor authentication to the following AWS IAM users in Account test/123456789:
          |
          |Username: username A
          |Last active: 365 days ago
          |
          |Here is some helpful documentation on:
          |
          |rotating credentials: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html,
          |
          |deleting users: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_users_manage.html#id_users_deleting_console,
          |
          |multi-factor authentication: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_mfa.html.
          |
          |For an overview of security vulnerabilities in your AWS account, see Security HQ (https://security-hq.gutools.co.uk/).
          |If you have any questions, please contact the Developer Experience team: devx@theguardian.com.
          |""".stripMargin,
        List.empty,
        List(AwsAccountTarget("123456789")),
        Preferred(Email),
        "Security HQ Credentials Notifier")
      val result = List(notification)
      makeCredentialsNotification(allCreds) shouldEqual result
    }
    "makes a credentials notification with a message notifying about old access keys only" in {
      val allCreds: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = Map(
        AwsAccount("", "", "", "") -> Left(FailedAttempt(List.empty)),
        AwsAccount("", "test", "", "123456789") -> Right(CredentialReportDisplay(
          new DateTime(2021, 1, 1, 1, 1),
          Seq(
            MachineUser("machine user A", AccessKey(AccessKeyDisabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, None, None, List.empty),
            MachineUser("machine user B", AccessKey(AccessKeyEnabled, Some(new DateTime(2018, 12, 12, 1, 1))), AccessKey(NoKey, None), Red, Some(243), None, List.empty),
            MachineUser("machine user C", AccessKey(NoKey, None), AccessKey(AccessKeyEnabled, Some(new DateTime(2015, 6, 5, 12, 1))), Red, Some(243), None, List.empty),
          ),
          Seq(
            HumanUser("username A", true, AccessKey(AccessKeyEnabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, Some(365), None, List.empty),
            HumanUser("username B", true, AccessKey(AccessKeyDisabled, Some(new DateTime(2020, 9, 1, 1, 1))), AccessKey(NoKey, None), Red, Some(150), None, List.empty),
          )
        ))
      )
      val notification: Notification = Notification(
        "Action required - The test AWS Account has old AWS credentials and/or credentials missing MFA",
        """
          |Please rotate the following IAM access keys in AWS Account test/123456789 or delete them if they are disabled and unused (if you're already planning to do this, please ignore this message):
          |
          |Username: machine user B
          |Key 1 last rotation: 12/12/2018
          |Key 2 last rotation: Unknown
          |Last active: 242 days ago
          |
          |
          |Username: machine user C
          |Key 1 last rotation: Unknown
          |Key 2 last rotation: 05/06/2015
          |Last active: 242 days ago
          |
          |
          |Username: username B
          |Key 1 last rotation: 01/09/2020
          |Key 2 last rotation: Unknown
          |Last active: 150 days ago
          |
          |Here is some helpful documentation on:
          |
          |rotating credentials: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html,
          |
          |deleting users: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_users_manage.html#id_users_deleting_console,
          |
          |multi-factor authentication: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_mfa.html.
          |
          |For an overview of security vulnerabilities in your AWS account, see Security HQ (https://security-hq.gutools.co.uk/).
          |If you have any questions, please contact the Developer Experience team: devx@theguardian.com.
          |""".stripMargin,
        List.empty,
        List(AwsAccountTarget("123456789")),
        Preferred(Email),
        "Security HQ Credentials Notifier")

      val result = List(notification)
      makeCredentialsNotification(allCreds) shouldEqual result
    }
    "makes a credentials notification with a message notifying about missing mfas only" in {
      val allCreds: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = Map(
        AwsAccount("", "", "", "") -> Left(FailedAttempt(List.empty)),
        AwsAccount("", "test", "", "123456789") -> Right(CredentialReportDisplay(
          new DateTime(2021, 1, 1, 1, 1),
          Seq.empty,
          Seq(
            HumanUser("username A", false, AccessKey(AccessKeyEnabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Red, Some(365), None, List.empty),
            HumanUser("username B", false, AccessKey(NoKey, None), AccessKey(AccessKeyEnabled, Some(DateTime.now().minusMonths(2))), Red, Some(150), None, List.empty),
          )
        ))
      )
      val notification: Notification = Notification(
        "Action required - The test AWS Account has old AWS credentials and/or credentials missing MFA",
        """
          |Please add multi-factor authentication to the following AWS IAM users in Account test/123456789:
          |
          |Username: username A
          |Last active: 365 days ago
          |
          |
          |Username: username B
          |Last active: 150 days ago
          |
          |Here is some helpful documentation on:
          |
          |rotating credentials: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html,
          |
          |deleting users: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_users_manage.html#id_users_deleting_console,
          |
          |multi-factor authentication: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_mfa.html.
          |
          |For an overview of security vulnerabilities in your AWS account, see Security HQ (https://security-hq.gutools.co.uk/).
          |If you have any questions, please contact the Developer Experience team: devx@theguardian.com.
          |""".stripMargin,
        List.empty,
        List(AwsAccountTarget("123456789")),
        Preferred(Email),
        "Security HQ Credentials Notifier")

      val result = List(notification)
      makeCredentialsNotification(allCreds) shouldEqual result
    }
    "returns a failure when there are no old access keys or missing mfas" in {
      val allCreds: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = Map(AwsAccount("", "", "", "") -> Left(FailedAttempt(List.empty)))
      val result = List.empty
      makeCredentialsNotification(allCreds) shouldEqual result
    }
  }
}
