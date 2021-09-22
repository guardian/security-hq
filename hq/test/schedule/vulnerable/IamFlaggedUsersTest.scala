package schedule.vulnerable

import model._
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}
import schedule.vulnerable.IamFlaggedUsers.{findMissingMfa, findOldAccessKeys}

class IamFlaggedUsersTest extends FreeSpec with Matchers {
  "IamFlaggedUsers" - {
    val oldHumanAccessKeyEnabled = AccessKey(AccessKeyEnabled, Some(DateTime.now.minusMonths(6)))
    val oldHumanAccessKeyDisabled = oldHumanAccessKeyEnabled.copy(keyStatus = AccessKeyDisabled)
    val oldHumanAccessKeyDisabledUser = HumanUser("", true, oldHumanAccessKeyDisabled, AccessKey(NoKey, None), Red(Seq(OutdatedKey)), None, None, List.empty)
    val oldHumanAccessKeyEnabledUser = oldHumanAccessKeyDisabledUser.copy(key1 = oldHumanAccessKeyEnabled)

    val oldMachineAccessKeyEnabled = AccessKey(AccessKeyEnabled, Some(DateTime.now.minusMonths(18)))
    val oldMachineAccessKeyDisabled = oldMachineAccessKeyEnabled.copy(keyStatus = AccessKeyDisabled)
    val oldMachineAccessKeyEnabledUser = MachineUser("", oldMachineAccessKeyEnabled, AccessKey(NoKey, None), Red(Seq(OutdatedKey)), None, None, List.empty)
    val oldMachineAccessKeyDisabledUser = oldMachineAccessKeyEnabledUser.copy(key1 = oldMachineAccessKeyDisabled)

    val newAccessKeyDisabled = AccessKey(AccessKeyDisabled, Some(DateTime.now().minusMonths(1)))
    val newAccessKeyEnabled = newAccessKeyDisabled.copy(keyStatus = AccessKeyEnabled)
    val newMachineAccessKeyDisabledUser = MachineUser("", newAccessKeyDisabled, AccessKey(NoKey, None), Green, None, None, List.empty)
    val newHumanAccessKeyEnabledUser = HumanUser("", true, newAccessKeyEnabled, AccessKey(NoKey, None), Green, None, None, List.empty)

    val humanMfaMissingUser = HumanUser("", false, newAccessKeyDisabled, AccessKey(NoKey, None), Red(Seq(MissingMfa)), None, None, List.empty)

    "findOldAccessKeys" - {
      "returns CredentialReportDisplays with access keys greater than 90 days old" in {
        val credsReport: CredentialReportDisplay =
          CredentialReportDisplay(
            new DateTime(2021, 1, 1, 1, 1),
            Seq(
              oldMachineAccessKeyEnabledUser,
              newMachineAccessKeyDisabledUser,
              oldMachineAccessKeyDisabledUser
            ),
            Seq(
              oldHumanAccessKeyDisabledUser,
              oldHumanAccessKeyEnabledUser,
              newHumanAccessKeyEnabledUser,
            )
          )

        val result: Seq[VulnerableUser] = Seq(
          oldMachineAccessKeyEnabledUser,
          // We've only temporarily commented out these cases as the logic ought to be reverted to align the dashboard and scheduled job
          // oldMachineAccessKeyDisabledUser,
          // oldHumanAccessKeyDisabledUser,
          oldHumanAccessKeyEnabledUser
        ).map(VulnerableUser.fromIamUser)

        findOldAccessKeys(credsReport) shouldEqual result
      }
      "returns empty human user and machine user lists when there are no access keys greater than 90/365 days old" in {
        val credsReport: CredentialReportDisplay = CredentialReportDisplay(
          new DateTime(2021, 1, 1, 1, 1),
          Seq(newMachineAccessKeyDisabledUser),
          Seq(newHumanAccessKeyEnabledUser)
        )

        findOldAccessKeys(credsReport) shouldEqual Seq.empty
      }
      "returns empty human user and machine user lists when there are no active access keys (even if older than 90/365 days)" in {
        val credsReport: CredentialReportDisplay = CredentialReportDisplay(
          new DateTime(2021, 1, 1, 1, 1),
          Seq(oldMachineAccessKeyDisabledUser),
          Seq(oldHumanAccessKeyDisabledUser)
        )

        findOldAccessKeys(credsReport) shouldEqual Seq.empty
      }
    }
    "findMissingMfa" - {
      "returns a list of human users in the credential report displays with missing Mfas" in {
        val credsReport: CredentialReportDisplay = CredentialReportDisplay(
          new DateTime(2021, 1, 1, 1, 1),
          Seq(newMachineAccessKeyDisabledUser),
          Seq(
            newHumanAccessKeyEnabledUser,
            humanMfaMissingUser
          )
        )
        val result = Seq(humanMfaMissingUser).map(VulnerableUser.fromIamUser)

        findMissingMfa(credsReport) shouldEqual result
      }
      "returns an empty human user list in the credential report displays when there are no human users with missing Mfas" in {
        val credsReport: CredentialReportDisplay = CredentialReportDisplay(
          new DateTime(2021, 1, 1, 1, 1),
          Seq(newMachineAccessKeyDisabledUser),
          Seq(
            newHumanAccessKeyEnabledUser,
            oldHumanAccessKeyDisabledUser
          )
        )
        findMissingMfa(credsReport) shouldEqual Seq.empty
      }
    }
  }
}
