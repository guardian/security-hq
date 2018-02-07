package logic

import model._
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}
import ReportDisplay._
import utils.attempt.FailedAttempt


class ReportDisplayTest extends FreeSpec with Matchers {

  "display logic" - {
    val now = DateTime.now()
    val cred = IAMCredential(
      "user",
      "user-1",
      now,
      None,
      Some(now.minusDays(1)),
      Some(now.minusDays(2)),
      Some(now.minusDays(3)),
      mfaActive = true,
      accessKey1Active = true,
      Some(now.minusDays(4)),
      Some(now.minusDays(5)),
      None,
      None,
      accessKey2Active = true,
      Some(now.minusDays(6)),
      Some(now.minusDays(7)),
      None,
      None,
      cert1Active = true,
      Some(now.minusDays(8)),
      cert2Active = true,
      Some(now.minusDays(9))
    )

    "find lastActivityDate" in {
      lastActivityDate(cred) shouldBe Some(now.minusDays(1))
    }

    "check key 1 status when key1 enabled" in {
      val cred2 =cred.copy(accessKey1Active = true)
      key1Status(cred2) shouldBe AccessKeyEnabled
    }

    "check key 1 status when key1 disabled" in {
      val cred2 =cred.copy(accessKey1Active = false)
      key1Status(cred2) shouldBe AccessKeyDisabled
    }

    "check key 1 status when there is no key1" in {
      val cred2 =cred.copy(accessKey1Active = false, accessKey1LastUsedDate = None)
      key1Status(cred2) shouldBe NoKey
    }

    "check key2 status when key2 enabled" in {
      val cred2 =cred.copy(accessKey2Active = true)
      key2Status(cred2) shouldBe AccessKeyEnabled
    }

    "check key2 status when key2 disabled" in {
      val cred2 =cred.copy(accessKey2Active = false)
      key2Status(cred2) shouldBe AccessKeyDisabled
    }

    "check key2 status when there is no key2" in {
      val cred2 =cred.copy(accessKey2Active = false, accessKey2LastUsedDate = None)
      key2Status(cred2) shouldBe NoKey
    }

    "machine report status green when key1 enabled" in {
      val machineCred = cred.copy(passwordEnabled = None, passwordLastUsed = None, accessKey2Active = false, accessKey1Active = true)
      machineReportStatus(machineCred) shouldBe Green
    }

    "machine report status green when key2 enabled" in {
      val machineCred = cred.copy(passwordEnabled = None, passwordLastUsed = None, accessKey2Active = true, accessKey1Active = false)
      machineReportStatus(machineCred) shouldBe Green
    }

    "check machine report status amber when no key enabled" in {
      val machineCred = cred.copy(passwordEnabled = None, passwordLastUsed = None, accessKey1Active = false, accessKey2Active = false)
      machineReportStatus(machineCred) shouldBe Amber
    }

    "check human report status green when mfa active and no active access key" in {
      val humanCred = cred.copy(accessKey1Active = false, accessKey2Active = false, mfaActive = true)
      humanReportStatus(humanCred) shouldBe Green
    }

    "check human report status green when mfa active and access key disabled" in {
      val humanCred = cred.copy(accessKey1Active = false, accessKey2Active = false, mfaActive = true, accessKey1LastUsedDate = Some(now))
      humanReportStatus(humanCred) shouldBe Green
    }

    "check human report status amber when key1 enabled" in {
      val humanCred = cred.copy(accessKey1Active = true, accessKey2Active = false, mfaActive = true)
      humanReportStatus(humanCred) shouldBe Amber
    }

    "check human report status amber when key2 enabled" in {
      val humanCred = cred.copy(accessKey1Active = false, accessKey2Active = true, mfaActive = true)
      humanReportStatus(humanCred) shouldBe Amber
    }

    "check human report status red when mfa not active" in {
      val humanCred = cred.copy(accessKey1Active = false, accessKey2Active = false, mfaActive = false)
      humanReportStatus(humanCred) shouldBe Red
    }

    "check credential display report for human user" in {
      val humanCred = cred.copy(passwordEnabled = Some(true))
      val iAMCredentialsReport = IAMCredentialsReport(now, List(humanCred))
      val humanUser = HumanUser(cred.user, cred.mfaActive, AccessKeyEnabled, AccessKeyEnabled, Amber, Some(1))
      val displayReport = CredentialReportDisplay(now, humanUsers = Seq(humanUser) )
      toCredentialReportDisplay(iAMCredentialsReport) shouldBe displayReport
    }

    "check credential display report for machine user" in {
      val iAMCredentialsReport = IAMCredentialsReport(now, List(cred))
      val machineUser = MachineUser(cred.user, AccessKeyEnabled, AccessKeyEnabled, Green, Some(1))
      val displayReport = CredentialReportDisplay(now, machineUsers = Seq(machineUser) )
      toCredentialReportDisplay(iAMCredentialsReport) shouldBe displayReport
    }

    "check credential display report - machine user and human user" in {
      val humanCred = cred.copy(passwordEnabled = Some(true))
      val humanUser = HumanUser(cred.user, cred.mfaActive, AccessKeyEnabled, AccessKeyEnabled, Amber, Some(1))
      val iAMCredentialsReport = IAMCredentialsReport(now, List(humanCred, cred))
      val machineUser = MachineUser(cred.user, AccessKeyEnabled, AccessKeyEnabled, Green, Some(1))
      val displayReport = CredentialReportDisplay(now, humanUsers = Seq(humanUser) , machineUsers = Seq(machineUser) )
      toCredentialReportDisplay(iAMCredentialsReport) shouldBe displayReport
    }

  }

  "sortAccountsByReportSummary" - {
    val now = DateTime.now()

    val humanRed = HumanUser("humanRed", hasMFA = false, NoKey, NoKey, Red, Some(1))
    val humanAmber = HumanUser("humanAmber", hasMFA = true, AccessKeyEnabled, AccessKeyEnabled, Amber, Some(1))
    val humanGreen = HumanUser("humanGreen", hasMFA = true, NoKey, NoKey, Green, Some(1))
    val machineAmber = MachineUser("machineAmber", AccessKeyDisabled, NoKey, Amber, Some(1))
    val machineGreen = MachineUser("machineGreen", AccessKeyEnabled, AccessKeyEnabled, Green, Some(1))

    val reportNoUsers = CredentialReportDisplay(now, humanUsers = Seq.empty, machineUsers = Seq.empty)
    val reportAllGreen = CredentialReportDisplay(now, humanUsers = Seq(humanGreen, humanGreen), machineUsers = Seq(machineGreen, machineGreen))
    val reportSomeWarnings = CredentialReportDisplay(now, humanUsers = Seq(humanGreen, humanAmber, humanGreen), machineUsers = Seq(machineAmber, machineGreen, machineGreen))
    val reportMediumConcern = CredentialReportDisplay(now, humanUsers = Seq(humanGreen, humanAmber, humanRed, humanAmber), machineUsers = Seq(machineAmber, machineAmber, machineGreen))
    val reportBadScenario = CredentialReportDisplay(now, humanUsers = Seq(humanGreen, humanAmber, humanRed, humanAmber), machineUsers = Seq(machineAmber, machineAmber, machineGreen))
    val reportWorstCase = CredentialReportDisplay(now, humanUsers = Seq(humanRed, humanAmber, humanRed, humanGreen), machineUsers = Seq(machineAmber, machineGreen))

    val awsAccA = AwsAccount("awsAccA", "Account A", "roleArnA")
    val awsAccB = AwsAccount("awsAccB", "Account B", "roleArnB")
    val awsAccC = AwsAccount("awsAccC", "Account C", "roleArnC")
    val awsAccD = AwsAccount("awsAccD", "Account D", "roleArnD")
    val awsAccE = AwsAccount("awsAccE", "Account E", "roleArnE")

    "will sort reports by account name if there are no alerts" in {
      val reports = List(
        (awsAccB, Right(reportNoUsers)),
        (awsAccC, Right(reportAllGreen)),
        (awsAccA, Right(reportAllGreen)),
        (awsAccE, Right(reportNoUsers)),
        (awsAccD, Right(reportAllGreen))
      )

      sortAccountsByReportSummary(reports) shouldEqual List(
        (awsAccA, Right(reportAllGreen)),
        (awsAccB, Right(reportNoUsers)),
        (awsAccC, Right(reportAllGreen)),
        (awsAccD, Right(reportAllGreen)),
        (awsAccE, Right(reportNoUsers))
      )
    }

    "will order accounts with unsuccessful reports before accounts with no alerts" in {
      val reports = List(
        (awsAccB, Right(reportAllGreen)),
        (awsAccC, Left(FailedAttempt)),
        (awsAccA, Left(FailedAttempt)),
        (awsAccE, Right(reportAllGreen)),
        (awsAccD, Left(FailedAttempt))
      )

      sortAccountsByReportSummary(reports) shouldEqual List(
        (awsAccA, Left(FailedAttempt)),
        (awsAccC, Left(FailedAttempt)),
        (awsAccD, Left(FailedAttempt)),
        (awsAccB, Right(reportAllGreen)),
        (awsAccE, Right(reportAllGreen))
      )
    }

    "will sort according to the number of red alerts, then amber alerts" in {
      val reports = List(
        (awsAccA, Right(reportSomeWarnings)),
        (awsAccB, Right(reportWorstCase)),
        (awsAccC, Right(reportBadScenario)),
        (awsAccD, Right(reportAllGreen)),
        (awsAccE, Right(reportMediumConcern))
      )

      sortAccountsByReportSummary(reports) shouldEqual List(
        (awsAccB, Right(reportWorstCase)),
        (awsAccC, Right(reportBadScenario)),
        (awsAccE, Right(reportMediumConcern)),
        (awsAccA, Right(reportSomeWarnings)),
        (awsAccD, Right(reportAllGreen))
      )
    }

    "will sort according to account name if the reports are equal" in {
      val reports = List(
        (awsAccC, Right(reportMediumConcern)),
        (awsAccD, Right(reportSomeWarnings)),
        (awsAccE, Right(reportMediumConcern)),
        (awsAccA, Right(reportMediumConcern)),
        (awsAccB, Right(reportSomeWarnings))
      )

      sortAccountsByReportSummary(reports) shouldEqual List(
        (awsAccA, Right(reportMediumConcern)),
        (awsAccC, Right(reportMediumConcern)),
        (awsAccE, Right(reportMediumConcern)),
        (awsAccB, Right(reportSomeWarnings)),
        (awsAccD, Right(reportSomeWarnings))
      )
    }
  }

  "reportStatusSummary" - {
    val now = DateTime.now()

    "returns zeros when there are no users" in {
      val report = CredentialReportDisplay(now)
      reportStatusSummary(report).warnings shouldEqual 0
      reportStatusSummary(report).errors shouldEqual 0
      reportStatusSummary(report).other shouldEqual 0
    }

    "returns individual counts for each report status" in {
      val humanRed = HumanUser("humanRed", hasMFA = false, AccessKeyEnabled, AccessKeyEnabled, Red, Some(1))
      val humanGreen = HumanUser("humanGreen", hasMFA = true, AccessKeyEnabled, AccessKeyEnabled, Green, Some(1))
      val machineAmber = MachineUser("machineAmber", AccessKeyEnabled, AccessKeyEnabled, Amber, Some(1))
      val machineBlue = MachineUser("machineGreen", AccessKeyEnabled, AccessKeyEnabled, Blue, Some(1))
      val machineGreen = MachineUser("machineGreen", AccessKeyEnabled, AccessKeyEnabled, Green, Some(1))
      val report = CredentialReportDisplay(
        now, humanUsers = Seq(humanRed, humanGreen), machineUsers = Seq(machineAmber, machineBlue, machineGreen, machineAmber, machineBlue, machineBlue)
      )
      reportStatusSummary(report).warnings shouldEqual 2
      reportStatusSummary(report).errors shouldEqual 1
      reportStatusSummary(report).other shouldEqual 3
    }
  }

  "sortUsersByReportSummary" - {
    val now = DateTime.now()

    val humanRedA = HumanUser("humanRedA", hasMFA = false, NoKey, NoKey, Red, Some(1))
    val humanRedB = HumanUser("humanRedB", hasMFA = false, NoKey, NoKey, Red, Some(1))
    val humanAmberA = HumanUser("humanAmberA", hasMFA = true, AccessKeyEnabled, AccessKeyEnabled, Amber, Some(1))
    val humanAmberB = HumanUser("humanAmberB", hasMFA = true, AccessKeyEnabled, AccessKeyEnabled, Amber, Some(1))
    val humanGreenA = HumanUser("humanGreenA", hasMFA = true, NoKey, NoKey, Green, Some(1))
    val humanGreenB = HumanUser("humanGreenB", hasMFA = true, NoKey, NoKey, Green, Some(1))

    val machineAmberA = MachineUser("machineAmberA", AccessKeyDisabled, NoKey, Amber, Some(1))
    val machineAmberB = MachineUser("machineAmberB", AccessKeyDisabled, NoKey, Amber, Some(1))
    val machineGreenA = MachineUser("machineGreenA", AccessKeyEnabled, AccessKeyEnabled, Green, Some(1))
    val machineGreenB = MachineUser("machineGreenB", AccessKeyEnabled, AccessKeyEnabled, Green, Some(1))

    "will sort according to username if the ReportStatuses are equal" in {
      val report = CredentialReportDisplay(now,
        machineUsers = Seq(machineGreenB, machineGreenA, machineGreenB, machineGreenA),
        humanUsers = Seq(humanAmberA, humanAmberB, humanAmberB, humanAmberA, humanAmberB)
      )

      sortUsersByReportSummary(report).machineUsers shouldEqual Seq(machineGreenA, machineGreenA, machineGreenB, machineGreenB)
      sortUsersByReportSummary(report).humanUsers shouldEqual Seq(humanAmberA, humanAmberA, humanAmberB, humanAmberB, humanAmberB)
    }

    "orders the humanUsers and machineUsers according to severity of the ReportStatus" in {
      val report = CredentialReportDisplay(now,
        machineUsers = Seq(machineAmberB, machineGreenA, machineAmberA, machineGreenB),
        humanUsers = Seq(humanGreenB, humanRedA, humanAmberB, humanAmberA, humanRedB, humanGreenA)
      )

      sortUsersByReportSummary(report).machineUsers shouldEqual Seq(machineAmberA, machineAmberB, machineGreenA, machineGreenB)
      sortUsersByReportSummary(report).humanUsers shouldEqual Seq(humanRedA, humanRedB, humanAmberA, humanAmberB, humanGreenA, humanGreenB)

    }
  }
}
