package logic

import model._
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}
import ReportDisplay._


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
      val machineGreen = MachineUser("machineGreen", AccessKeyEnabled, AccessKeyEnabled, Green, Some(1))
      val report = CredentialReportDisplay(now, humanUsers = Seq(humanRed, humanGreen), machineUsers = Seq(machineAmber, machineGreen, machineGreen, machineAmber))
      reportStatusSummary(report).warnings shouldEqual 2
      reportStatusSummary(report).errors shouldEqual 1
      reportStatusSummary(report).other shouldEqual 3
    }
  }
}
