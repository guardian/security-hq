package schedule.vulnerable

import model._
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}
import schedule.vulnerable.IamFlaggedUsers.{findMissingMfa, findOldAccessKeys}

class IamFlaggedUsersTest extends FreeSpec with Matchers {
  "findOldCredentialsAndMissingMfas" - {
    "findOldAccessKeys" - {
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
    }
    "findMissingMfa" - {
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
    }
  }
}
