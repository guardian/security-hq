package aws.iam

import com.amazonaws.regions.{Region, Regions}
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.{FreeSpec, Matchers, OptionValues}


class CredentialsReportTest extends FreeSpec with Matchers with OptionValues {
  "credentials report" - {
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
          'user ("<root_account>"),
          'arn ("arn:aws:iam::0123456789:root"),
          'creationTime (new DateTime(2015, 6, 24, 12, 45, 0, DateTimeZone.UTC)),
          'passwordEnabled (None),
          'passwordLastUsed (Some(new DateTime(2016, 10, 7, 14, 30, 0, DateTimeZone.UTC))),
          'passwordLastChanged (None),
          'passwordNextRotation (None),
          'mfaActive (true),
          'accessKey1Active (false),
          'accessKey1LastRotated (None),
          'accessKey1LastUsedDate (None),
          'accessKey1LastUsedRegion (None),
          'accessKey1LastUsedService (None),
          'accessKey2Active (false),
          'accessKey2LastRotated (None),
          'accessKey2LastUsedDate (None),
          'accessKey2LastUsedRegion (None),
          'accessKey2LastUsedService (None),
          'cert1Active (false),
          'cert1LastRotated (None),
          'cert2Active (false),
          'cert2LastRotated (None)
        )
      }

      "parses normal user" in {
        val user = CredentialsReport.parseCredentialsReport(testReport).find(_.user == "IAM-user").value
        user should have (
          'user ("IAM-user"),
          'arn ("arn:aws:iam::0123456789:user/IAM-user"),
          'creationTime (new DateTime(2015, 6, 24, 15, 45, 0, DateTimeZone.UTC)),
          'passwordEnabled (Some(true)),
          'passwordLastUsed (Some(new DateTime(2017, 3, 8, 19, 35, 1, DateTimeZone.UTC))),
          'passwordLastChanged (Some(new DateTime(2016, 4, 29, 16, 20, 0, DateTimeZone.UTC))),
          'passwordNextRotation (None),
          'mfaActive (true),
          'accessKey1Active (false),
          'accessKey1LastRotated (None),
          'accessKey1LastUsedDate (None),
          'accessKey1LastUsedRegion (None),
          'accessKey1LastUsedService (None),
          'accessKey2Active (false),
          'accessKey2LastRotated (None),
          'accessKey2LastUsedDate (None),
          'accessKey2LastUsedRegion (None),
          'accessKey2LastUsedService (None),
          'cert1Active (false),
          'cert1LastRotated (None),
          'cert2Active (false),
          'cert2LastRotated (None)
        )
      }

      "parses user credentials" in {
        val userWithCreds = CredentialsReport.parseCredentialsReport(testReport).find(_.user == "credential-user").value
        userWithCreds should have (
          'accessKey1Active (true),
          'accessKey1LastRotated (Some(new DateTime(2015, 10, 22, 14, 45, 0, DateTimeZone.UTC))),
          'accessKey1LastUsedDate (Some(new DateTime(2017, 8, 30, 13, 32, 0, DateTimeZone.UTC))),
          'accessKey1LastUsedRegion (Some(Region.getRegion(Regions.EU_WEST_1))),
          'accessKey1LastUsedService (Some("ec2"))
        )
      }
    }
  }
}
