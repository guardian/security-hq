package schedule

import com.gu.anghammarad.models.Stack
import model.{UserWithOutdatedKeys, _}
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}
import schedule.IamAudit._
import schedule.IamMessages.createMessage

class IamAuditTest extends FreeSpec with Matchers {
  val outdatedUser1: UserWithOutdatedKeys = UserWithOutdatedKeys("lesleyKnope", Some(DateTime.now.minusDays(400)), None, None, List())
  val outdatedUser2: UserWithOutdatedKeys = UserWithOutdatedKeys("ronSwanson", Some(DateTime.now.minusDays(400)), None, None, List())
  val outdatedUser3: UserWithOutdatedKeys = UserWithOutdatedKeys("tomHaverford", Some(DateTime.now.minusDays(400)), None, None, List())

  val noMfaUser1: UserNoMfa = UserNoMfa("april", None, List())
  val noMfaUser2: UserNoMfa = UserNoMfa("andy", None, List())
  val noMfaUser3: UserNoMfa = UserNoMfa("diane", None, List())


  "getNotificationTargetGroups" - {
    "correctly builds target groups when users have no tags " in {
      val targetGroups = getNotificationTargetGroups(VulnerableUsers(Seq(outdatedUser1, outdatedUser2), Seq(noMfaUser1, noMfaUser2)))
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

      val targetGroups = getNotificationTargetGroups(VulnerableUsers(inputOutdatedUsers, inputNoMfaUsers))

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
      val targetGroups = getNotificationTargetGroups(VulnerableUsers(Seq(), Seq(noMfaUser1, noMfaUser2)))
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
    "returns nothing when there are no old access keys or missing mfas" in {
      val allCreds = Map(AwsAccount("", "", "", "") -> Seq.empty)
      val result = List.empty
      makeIamNotification(allCreds) shouldEqual result
    }
  }
}
