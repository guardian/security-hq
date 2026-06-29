package aws.iam

import aws.iam.CredentialsReport.credentialsReportReadyForRefresh
import model.{AwsStack, CredentialReportDisplay, IAMCredential, IAMCredentialsReport}
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.OptionValues
import software.amazon.awssdk.regions.Region
import utils.attempt.{AttemptValues, FailedAttempt, Failure}

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class CredentialsReportTest extends AnyFreeSpec with Matchers with OptionValues with AttemptValues {

  "credentialsReportNeedsRefreshing" - {
    "when the report is at least 4 hours old" - {
      val now = new DateTime(2021, 5, 6, 16, 43)
      "returns false for exactly 4 hours old" in {
        val credentialGenerationTime = new DateTime(2021, 5, 6, 12, 43)
        val result = credentialsReportReadyForRefresh(Right(CredentialReportDisplay(credentialGenerationTime)), now)
        result shouldEqual false
      }

      "returns true for just over 4 hours old" in {
        val credentialGenerationTime = new DateTime(2021, 5, 6, 12, 42)
        val result = credentialsReportReadyForRefresh(Right(CredentialReportDisplay(credentialGenerationTime)), now)
        result shouldEqual true
      }

      "returns true for way over 4 hours old" in {
        val credentialGenerationTime = new DateTime(2021, 5, 5, 12, 42)
        val result = credentialsReportReadyForRefresh(Right(CredentialReportDisplay(credentialGenerationTime)), now)
        result shouldEqual true
      }
    }

    "returns false when report is less than 4 hours old" in {
      val credentialGenerationTime = new DateTime(2021, 5, 6, 12, 44)
      val credentialReportDisplay = CredentialReportDisplay(credentialGenerationTime)
      val now = new DateTime(2021, 5, 6, 16, 43)
      val result = credentialsReportReadyForRefresh(Right(credentialReportDisplay), now)
      result shouldEqual false
    }

    "returns true if the argument is a failure" in {
      val result = credentialsReportReadyForRefresh(
        Left(FailedAttempt(Failure("Test error", "Test error", 500))),
        new DateTime(2021, 5, 6, 16, 43)
      )
      result shouldEqual true
    }
  }

  "credentials report" - {
    val testReportNoPasswordLastUsed = """user,arn,user_creation_time,password_enabled,password_last_used,password_last_changed,password_next_rotation,mfa_active,access_key_1_active,access_key_1_last_rotated,access_key_1_last_used_date,access_key_1_last_used_region,access_key_1_last_used_service,access_key_2_active,access_key_2_last_rotated,access_key_2_last_used_date,access_key_2_last_used_region,access_key_2_last_used_service,cert_1_active,cert_1_last_rotated,cert_2_active,cert_2_last_rotated
                       |credential-user,arn:aws:iam::0123456789:user/credential-user,2015-10-22T16:40:00+00:00,false,no_information,N/A,N/A,false,true,2015-10-22T14:45:00+00:00,2017-08-30T13:32:00+00:00,eu-west-1,ec2,false,N/A,N/A,N/A,N/A,false,N/A,false,N/A""".stripMargin


    val testReport = """user,arn,user_creation_time,password_enabled,password_last_used,password_last_changed,password_next_rotation,mfa_active,access_key_1_active,access_key_1_last_rotated,access_key_1_last_used_date,access_key_1_last_used_region,access_key_1_last_used_service,access_key_2_active,access_key_2_last_rotated,access_key_2_last_used_date,access_key_2_last_used_region,access_key_2_last_used_service,cert_1_active,cert_1_last_rotated,cert_2_active,cert_2_last_rotated
                       |<root_account>,arn:aws:iam::0123456789:root,2015-06-24T12:45:00+00:00,not_supported,2016-10-07T14:30:00+00:00,not_supported,not_supported,true,false,N/A,N/A,N/A,N/A,false,N/A,N/A,N/A,N/A,false,N/A,false,N/A
                       |IAM-user,arn:aws:iam::0123456789:user/IAM-user,2015-06-24T15:45:00+00:00,true,2017-03-08T19:35:01+00:00,2016-04-29T16:20:00+00:00,N/A,true,false,N/A,N/A,N/A,N/A,false,N/A,N/A,N/A,N/A,false,N/A,false,N/A
                       |with-disabled-key,arn:aws:iam::0123456789:user/with-disabled-key,2016-09-30T09:20:00+00:00,false,2016-11-25T11:20:00+00:00,N/A,N/A,false,false,2017-04-19T15:47:39+00:00,2017-04-19T15:48:00+00:00,eu-west-1,ec2,true,2016-07-29T09:20:20+00:00,2017-05-24T11:35:00+00:00,us-east-1,sts,false,N/A,false,N/A
                       |different-region,arn:aws:iam::0123456789:user/different-region,2015-05-21T14:50:00+00:00,false,N/A,N/A,N/A,false,true,2015-05-28T15:00:00+00:00,2016-04-04T11:18:00+00:00,us-east-1,iam,false,N/A,N/A,N/A,N/A,false,N/A,false,N/A
                       |credential-user,arn:aws:iam::0123456789:user/credential-user,2015-10-22T16:40:00+00:00,false,N/A,N/A,N/A,false,true,2015-10-22T14:45:00+00:00,2017-08-30T13:32:00+00:00,eu-west-1,ec2,false,N/A,N/A,N/A,N/A,false,N/A,false,N/A""".stripMargin

    "parseCredentialsReport" - {
      "successfully parses example report" in {
        CredentialsReport.parseCredentialsReport(testReport) should have length 5
      }

      "parses root user" in {
        val rootUser = CredentialsReport.parseCredentialsReport(testReport).find(_.rootUser).value
        rootUser should have (
          Symbol("user") ("<root_account>"),
          Symbol("arn") ("arn:aws:iam::0123456789:root"),
          Symbol("creationTime") (new DateTime(2015, 6, 24, 12, 45, 0, DateTimeZone.UTC)),
          Symbol("passwordEnabled") (None),
          Symbol("passwordLastUsed") (Some(new DateTime(2016, 10, 7, 14, 30, 0, DateTimeZone.UTC))),
          Symbol("passwordLastChanged") (None),
          Symbol("passwordNextRotation") (None),
          Symbol("mfaActive") (true),
          Symbol("accessKey1Active") (false),
          Symbol("accessKey1LastRotated") (None),
          Symbol("accessKey1LastUsedDate") (None),
          Symbol("accessKey1LastUsedRegion") (None),
          Symbol("accessKey1LastUsedService") (None),
          Symbol("accessKey2Active") (false),
          Symbol("accessKey2LastRotated") (None),
          Symbol("accessKey2LastUsedDate") (None),
          Symbol("accessKey2LastUsedRegion") (None),
          Symbol("accessKey2LastUsedService") (None),
          Symbol("cert1Active") (false),
          Symbol("cert1LastRotated") (None),
          Symbol("cert2Active") (false),
          Symbol("cert2LastRotated") (None)
        )
      }

      "parses normal user" in {
        val user = CredentialsReport.parseCredentialsReport(testReport).find(_.user == "IAM-user").value
        user should have (
          Symbol("user") ("IAM-user"),
          Symbol("arn") ("arn:aws:iam::0123456789:user/IAM-user"),
          Symbol("creationTime") (new DateTime(2015, 6, 24, 15, 45, 0, DateTimeZone.UTC)),
          Symbol("passwordEnabled") (Some(true)),
          Symbol("passwordLastUsed") (Some(new DateTime(2017, 3, 8, 19, 35, 1, DateTimeZone.UTC))),
          Symbol("passwordLastChanged") (Some(new DateTime(2016, 4, 29, 16, 20, 0, DateTimeZone.UTC))),
          Symbol("passwordNextRotation") (None),
          Symbol("mfaActive") (true),
          Symbol("accessKey1Active") (false),
          Symbol("accessKey1LastRotated") (None),
          Symbol("accessKey1LastUsedDate") (None),
          Symbol("accessKey1LastUsedRegion") (None),
          Symbol("accessKey1LastUsedService") (None),
          Symbol("accessKey2Active") (false),
          Symbol("accessKey2LastRotated") (None),
          Symbol("accessKey2LastUsedDate") (None),
          Symbol("accessKey2LastUsedRegion") (None),
          Symbol("accessKey2LastUsedService") (None),
          Symbol("cert1Active") (false),
          Symbol("cert1LastRotated") (None),
          Symbol("cert2Active") (false),
          Symbol("cert2LastRotated") (None)
        )
      }

      "parses user credentials" in {
        val userWithCreds = CredentialsReport.parseCredentialsReport(testReport).find(_.user == "credential-user").value
        userWithCreds should have (
          Symbol("accessKey1Active") (true),
          Symbol("accessKey1LastRotated") (Some(new DateTime(2015, 10, 22, 14, 45, 0, DateTimeZone.UTC))),
          Symbol("accessKey1LastUsedDate") (Some(new DateTime(2017, 8, 30, 13, 32, 0, DateTimeZone.UTC))),
          Symbol("accessKey1LastUsedRegion") (Some(Region.of("eu-west-1"))),
          Symbol("accessKey1LastUsedService") (Some("ec2"))
        )
      }

      "fails when parsing empty CSV" in {
        val testReport = ""
        CredentialsReport.tryParsingReport(testReport).isFailedAttempt() shouldBe true
      }

      "fails when parsing invalid CSV" in {
        val testReport = """user,arn,user_creation_time,password_enabled,password_last_used
                           |<root_account>,arn:aws:iam::0123456789:root,2015-06-24T12:45:00+00:00,not_supported,2016-10-07T14:30:00+00:00"""
        CredentialsReport.tryParsingReport(testReport).isFailedAttempt() shouldBe true
      }

      "handles - 'no_information' data for password_last_used field" in {
        val userWithCreds = CredentialsReport.parseCredentialsReport(testReportNoPasswordLastUsed).find(_.user == "credential-user").value
        userWithCreds should have (
          Symbol("passwordLastUsed") (None)
        )
      }
    }
  }

  "enrichReportWithStackDetails" - {
    val now = DateTime.now()
    val stackId = "arn:aws:cloudformation:eu-west-1:123456789123:stack/stack-name/8a123bc0-222d-33e4-5fg6-77aa88b12345"
    val stackA = AwsStack(stackId, "stackfoo", "eu-west-1")
    val stackB = AwsStack(stackId, "stackbar", "eu-west-1")
    val stackC = AwsStack(stackId, "stackbaz", "eu-west-1")
    val cred = IAMCredential(
      "username",
      "arn:xyz",
      now,
      None,
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

    "will discover the stack associated with the credential, and update the report" in {
      val report = IAMCredentialsReport(now, List(cred.copy(user = "stackbar-username-345EFG1ABC2D")))
      val result = CredentialsReport.enrichReportWithStackDetails(report, List(stackA, stackB, stackC)).entries
      result shouldEqual List(cred.copy(user = "stackbar-username-345EFG1ABC2D", stack = Some(stackB)))
    }

    "will avoid false positives, by checking for the autogenerated, alphanumeric string" in {
      val report = IAMCredentialsReport(now, List(cred.copy(user = "stackbar-username-customstring")))
      val result = CredentialsReport.enrichReportWithStackDetails(report, List(stackA, stackB, stackC)).entries
      result shouldEqual List(cred.copy(user = "stackbar-username-customstring"))
    }

    "will return the report unchanged if no associated stacks are found" in {
      val report = IAMCredentialsReport(now, List(cred.copy(user = "custom-user")))
      val result = CredentialsReport.enrichReportWithStackDetails(report, List(stackA, stackB, stackC)).entries
      result shouldEqual List(cred.copy(user = "custom-user"))
    }
  }
}
