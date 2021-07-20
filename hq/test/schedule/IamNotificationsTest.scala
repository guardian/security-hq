package schedule

import com.gu.anghammarad.models.Stack
import model._
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}
import schedule.IamDeadline.{getNearestDeadline, isFinalAlert, isWarningAlert}
import schedule.IamDisableAccessKeys.{areRotationDatesEqual, whichRotationDateIsOlder}
import schedule.IamFlaggedUsers.{findMissingMfa, findOldAccessKeys}
import schedule.IamNotifications._
import schedule.IamTargetGroups.getNotificationTargetGroups
import schedule.IamUsersToDisable.toDisableToday

class IamNotificationsTest extends FreeSpec with Matchers {
  val outdatedUser1 = VulnerableUser("lesleyKnope", tags = List())
  val outdatedUser2 = VulnerableUser("ronSwanson", tags = List())
  val outdatedUser3 = VulnerableUser("tomHaverford", tags = List())

  val noMfaUser1 = VulnerableUser("april", tags = List())
  val noMfaUser2 = VulnerableUser("andy", tags = List())
  val noMfaUser3 = VulnerableUser("diane", tags = List())


  "getNotificationTargetGroups" - {
    "correctly builds target groups when users have no tags " in {
      val targetGroups = getNotificationTargetGroups(Seq(outdatedUser1, outdatedUser2, noMfaUser1, noMfaUser2))
      targetGroups.length shouldEqual 1
      targetGroups.head.users.length shouldEqual 4
    }

    "correctly builds target groups when users have tags" in {
      val parks = Tag("Stack", "parks")
      val recreation = Tag("Stack", "recreation")

      val users = Seq(
        outdatedUser1,
        outdatedUser2.copy(tags=List(parks)),
        outdatedUser3.copy(tags=List(recreation))
      )

      val targetGroups = getNotificationTargetGroups(users)

      targetGroups.length shouldEqual 3

      val parkGroup = targetGroups.find(t => t.targets.exists{
        case Stack(stack) => stack == "parks"
        case _ => false
      })
      parkGroup shouldBe defined
      val parkGroupUsers = parkGroup.get.users
      parkGroupUsers.head.username shouldBe outdatedUser2.username

      val noTagGroup = targetGroups.find(t => t.targets.isEmpty)
      noTagGroup shouldBe defined
      noTagGroup.get.users shouldEqual List(outdatedUser1)

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
      val alert1 = IamAuditAlert(DateTime.now.minusWeeks(3), nearestDeadline)
      val alert2 = IamAuditAlert(DateTime.now.minusWeeks(3), DateTime.now.plusDays(2).withTimeAtStartOfDay)
      getNearestDeadline(List(alert1, alert2)) shouldEqual nearestDeadline
    }
    "returns true if the last rotation dates are the same" in {
      val date1: DateTime = DateTime.now.minusMonths(3)
      val date2: DateTime = DateTime.now.minusMonths(3)
      areRotationDatesEqual(date1, date2) shouldBe true
    }
    "returns false if the last rotation dates are not the same" in {
      val date1: DateTime = DateTime.now.minusMonths(3)
      val date2: DateTime = DateTime.now.minusMonths(4)
      areRotationDatesEqual(date1, date2) shouldBe false
    }
    "returns None when there are no dates to compare" in {
      val date1 = None
      val date2 = None
      whichRotationDateIsOlder(date1, date2) shouldEqual None
    }
    "if only one date is supplied, it is returned" in {
      val date1 = Some(DateTime.now.minusMonths(4))
      val date2 = None
      whichRotationDateIsOlder(date1, date2) shouldEqual date1
    }
    "returns the date that is furthest in the past" in {
      val date1: Option[DateTime] = Some(DateTime.now.minusMonths(4))
      val date2: Option[DateTime] = Some(DateTime.now.minusMonths(3))
      whichRotationDateIsOlder(date1, date2) shouldEqual date1
    }
  }
}
