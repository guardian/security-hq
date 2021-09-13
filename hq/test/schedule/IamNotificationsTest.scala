package schedule

import model._
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}
import schedule.IamNotifications._
import schedule.IamUsersToDisable.toDisableToday
import schedule.vulnerable.IamDeadline.{getNearestDeadline, isFinalAlert, isWarningAlert}
import schedule.vulnerable.IamFlaggedUsers.{findMissingMfa, findOldAccessKeys}

class IamNotificationsTest extends FreeSpec with Matchers {
  val outdatedUser1 = VulnerableUser("lesleyKnope", humanUser = true, tags = List())
  val outdatedUser2 = VulnerableUser("ronSwanson", humanUser = true, tags = List())
  val outdatedUser3 = VulnerableUser("tomHaverford", humanUser = true, tags = List())

  val noMfaUser1 = VulnerableUser("april", humanUser = true, tags = List())
  val noMfaUser2 = VulnerableUser("andy", humanUser = true, tags = List())
  val noMfaUser3 = VulnerableUser("diane", humanUser = true, tags = List())



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
            MachineUser("", oldMachineAccessKeyEnabled, AccessKey(NoKey, None), Red(Seq(OutdatedKey)), None, None, List.empty),
            MachineUser("", AccessKey(AccessKeyDisabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Green, None, None, List.empty),
            MachineUser("", oldMachineAccessKeyDisabled, AccessKey(NoKey, None), Red(Seq(OutdatedKey)), None, None, List.empty),
          ),
          Seq(
            HumanUser("", true, oldHumanAccessKeyDisabled, AccessKey(NoKey, None), Red(Seq(OutdatedKey)), None, None, List.empty),
            HumanUser("", true, oldHumanAccessKeyEnabled, AccessKey(NoKey, None), Red(Seq(OutdatedKey)), None, None, List.empty),
            HumanUser("", true, AccessKey(AccessKeyEnabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Amber, None, None, List.empty),
          )
        )
      val result: CredentialReportDisplay =
        CredentialReportDisplay(
          new DateTime(2021, 1, 1, 1, 1),
          Seq(
            MachineUser("", oldMachineAccessKeyEnabled, AccessKey(NoKey, None), Red(Seq(OutdatedKey)), None, None, List.empty),
            // We've only temporarily commented out these cases as the logic ought to be reverted to align the dashboard and scheduled job
            // MachineUser("", oldMachineAccessKeyDisabled, AccessKey(NoKey, None), Red(Seq(OutdatedKey)), None, None, List.empty),
          ),
          Seq(
            // HumanUser("", true, oldHumanAccessKeyDisabled, AccessKey(NoKey, None), Red(Seq(OutdatedKey)), None, None, List.empty),
            HumanUser("", true, oldHumanAccessKeyEnabled, AccessKey(NoKey, None), Red(Seq(OutdatedKey)), None, None, List.empty),
          )
        )
      findOldAccessKeys(credsReport) shouldEqual result
    }
    "returns empty human user and machine user lists when there are no access keys greater than 90 days old" in {
      val credsReport: CredentialReportDisplay = CredentialReportDisplay(
        new DateTime(2021, 1, 1, 1, 1),
        Seq(
          MachineUser("", AccessKey(AccessKeyDisabled, Some(DateTime.now().minusMonths(11))), AccessKey(NoKey, None), Green, None, None, List.empty),
        ),
        Seq(
          HumanUser("", true, AccessKey(AccessKeyEnabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Green, None, None, List.empty),
        )
      )
      val result: CredentialReportDisplay = CredentialReportDisplay(
        reportDate = new DateTime(2021, 1, 1, 1, 1),
        machineUsers = Seq.empty,
        humanUsers = Seq.empty
      )
      findOldAccessKeys(credsReport) shouldEqual result
    }
    "returns empty human user and machine user lists when there are no active access keys (even if older than 90/365 days)" in {
      val credsReport: CredentialReportDisplay = CredentialReportDisplay(
        new DateTime(2021, 1, 1, 1, 1),
        Seq(
          MachineUser("", AccessKey(AccessKeyDisabled, Some(DateTime.now().minusMonths(18))), AccessKey(NoKey, None), Red(Seq(OutdatedKey)), None, None, List.empty),
        ),
        Seq(
          HumanUser("", true, AccessKey(AccessKeyDisabled, Some(DateTime.now().minusMonths(6))), AccessKey(NoKey, None), Red(Seq(OutdatedKey)), None, None, List.empty),
        )
      )
      val result: CredentialReportDisplay = CredentialReportDisplay(
        reportDate = new DateTime(2021, 1, 1, 1, 1),
        machineUsers = Seq.empty,
        humanUsers = Seq.empty
      )
      findOldAccessKeys(credsReport) shouldEqual result
    }
    "returns a list of human users in the credential report displays with missing Mfas" in {
      val credsReport: CredentialReportDisplay = CredentialReportDisplay(
        new DateTime(2021, 1, 1, 1, 1),
        Seq(
          MachineUser("", AccessKey(AccessKeyDisabled, Some(DateTime.now().minusMonths(10))), AccessKey(NoKey, None), Green, None, None, List.empty),
        ),
        Seq(
          HumanUser("", true, AccessKey(AccessKeyEnabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Green, None, None, List.empty),
          HumanUser("", false, AccessKey(AccessKeyDisabled, Some(new DateTime(2020, 9, 1, 1, 1))), AccessKey(NoKey, None), Red(Seq(MissingMfa)), None, None, List.empty),
        )
      )
      val result: CredentialReportDisplay = CredentialReportDisplay(
        new DateTime(2021, 1, 1, 1, 1),
        Seq.empty,
        Seq(
          HumanUser("", false, AccessKey(AccessKeyDisabled, Some(new DateTime(2020, 9, 1, 1, 1))), AccessKey(NoKey, None), Red(Seq(MissingMfa)), None, None, List.empty),
        )
      )
      findMissingMfa(credsReport) shouldEqual result
    }
    "returns an empty human user list in the credential report displays when there are no human users with missing Mfas" in {
      val credsReport: CredentialReportDisplay = CredentialReportDisplay(
        new DateTime(2021, 1, 1, 1, 1),
        Seq(
          MachineUser("", AccessKey(AccessKeyDisabled, Some(DateTime.now().minusMonths(9))), AccessKey(NoKey, None), Green, None, None, List.empty),
        ),
        Seq(
          HumanUser("", true, AccessKey(AccessKeyEnabled, Some(DateTime.now().minusMonths(1))), AccessKey(NoKey, None), Green, None, None, List.empty),
          HumanUser("", true, AccessKey(AccessKeyDisabled, Some(new DateTime(2020, 9, 1, 1, 1))), AccessKey(NoKey, None), Red(Seq(OutdatedKey)), None, None, List.empty),
        )
      )
      val result: CredentialReportDisplay = CredentialReportDisplay(
        new DateTime(2021, 1, 1, 1, 1),
        Seq.empty,
        Seq.empty
      )
      findMissingMfa(credsReport) shouldEqual result
    }
    "returns nothing when there are no old access keys or missing mfas" in {
      val allCreds = Map(AwsAccount("", "", "", "") -> Seq.empty)
      val result = List.empty
      makeNotification(allCreds) shouldEqual result
    }
    "returns true when the deadline is one week away" in {
      val deadline = DateTime.now.plusWeeks(1)
      val today = DateTime.now
      isWarningAlert(deadline, today) shouldBe true
    }
    "returns true when the deadline is three weeks away" in {
      val deadline = DateTime.now.plusWeeks(3)
      val today = DateTime.now
      isWarningAlert(deadline, today) shouldBe true
    }
    "returns false when the deadline is not one or three weeks away" in {
      val deadline = DateTime.now.plusWeeks(2)
      val today = DateTime.now
      isWarningAlert(deadline, today) shouldBe false
    }
    "returns true when the deadline is the next day" in {
      val deadline = DateTime.now.plusDays(1)
      val today = DateTime.now
      isFinalAlert(deadline, today) shouldBe true
    }
    "returns false when the deadline is not the next day" in {
      val deadline = DateTime.now.plusDays(2)
      val today = DateTime.now
      isFinalAlert(deadline, today) shouldBe false
    }
    "returns true when the deadline is today" in {
      val deadline = DateTime.now
      val today = DateTime.now
      toDisableToday(deadline, today) shouldBe true
    }
    "returns false when the deadline is not today" in {
      val deadline = DateTime.now.minusDays(1)
      toDisableToday(deadline) shouldBe false
    }
    "returns nearest deadline" in {
      val nearestDeadline = DateTime.now.plusDays(1).withTimeAtStartOfDay
      val alert1 = IamAuditAlert(VulnerableCredential, DateTime.now.minusWeeks(3), nearestDeadline)
      val alert2 = IamAuditAlert(VulnerableCredential, DateTime.now.minusWeeks(3), DateTime.now.plusDays(2).withTimeAtStartOfDay)
      getNearestDeadline(List(alert1, alert2)) shouldEqual nearestDeadline
    }
  }
}
