package schedule.unrecognised

import com.gu.janus
import com.gu.janus.model.{ACL, JanusData, SupportACL}
import model._
import org.apache.commons.io.IOUtils
import org.joda.time.{DateTime, Seconds}
import org.scalatest.{FreeSpec, Matchers}
import schedule.unrecognised.IamUnrecognisedUsers._
import utils.attempt.{FailedAttempt, Failure}

import java.io.FileInputStream
import scala.io.Source

class IamUnrecognisedUsersTest extends FreeSpec with Matchers {
  val humanUser1 = HumanUser("ade.bimbola", true, AccessKey(NoKey, None), AccessKey(NoKey, None), Green, None, None, List(Tag(USERNAME_TAG_KEY, "ade.bimbola")))
  val humanUser2 = humanUser1.copy(username = "john.akindele", tags = List(Tag(USERNAME_TAG_KEY, "john.akindele")))
  val humanUser3 = humanUser1.copy(username = "khadija.omodara", tags = List(Tag(USERNAME_TAG_KEY, "khadija.omodara")))
  val humanUser4 = humanUser1.copy(username = "nneka.obi", tags = List(Tag(USERNAME_TAG_KEY, "nneka.obi")))
  val humanUser5 = humanUser1.copy(username = "john.doe", tags = List())
  val humanUser6 = humanUser1.copy(username = "jane.fonda", tags = List(Tag(USERNAME_TAG_KEY, "jane.fonda")))
  val allJanusUsernames = List("ade.bimbola", "john.akindele", "khadija.omodara", "nneka.obi", "jane.fonda")

  "findUnrecognisedIamUsers" - {
    "output janus username from JanusData type" in {
      val dummyJanusData = JanusData(
        Set(janus.model.AwsAccount("Deploy Tools", "deployTools")),
        ACL(Map("firstName.secondName" -> Set.empty)),
        ACL(Map.empty),
        SupportACL(Map.empty, Set.empty, Seconds.ZERO),
        None
      )

      getJanusUsernames(dummyJanusData) shouldEqual List("firstName.secondName")
    }
  }

  "getCredsReportDisplayForAccount" - {
    val failedAttempt: FailedAttempt = FailedAttempt(Failure("error", "error", 500))

    "if the either is a left, an empty list is output" in {
      val accountCredsLeft = Map(1 -> Left(failedAttempt))
      getCredsReportDisplayForAccount(accountCredsLeft) shouldEqual Nil
    }
    "if the either is a right, it is returned" in {
      val accountCredsRight = Map(1 -> Right(1))
      getCredsReportDisplayForAccount(accountCredsRight) shouldEqual List((1,1))
    }
    "given an empty map, return an empty list" in {
      getCredsReportDisplayForAccount(Map.empty) shouldEqual Nil
    }
    "if all eithers are a left, return an empty list" in {
      val accountCredsAllLeft = Map(1 -> Left(failedAttempt), 2 -> Left(failedAttempt), 3 -> Left(failedAttempt))
      getCredsReportDisplayForAccount(accountCredsAllLeft) shouldEqual Nil
    }
    "if every either is a right, output list contains same number of elements" in {
      val accountCredsAllRight = Map(1 -> Right(1), 2 -> Right(2), 3 -> Right(3))
      getCredsReportDisplayForAccount(accountCredsAllRight) should have length 3
    }
  }

  "unrecognisedUsersForAllowedAccounts" - {
    val credentialReportDisplay1 = CredentialReportDisplay(
      new DateTime(2021,10,7,14, 58),
      Seq.empty,
      Seq(humanUser1, humanUser2)
    )
    val awsAccount1 = AwsAccount("aws-account-1", "aws account 1", "", "12345")
    val credentialReportDisplay2 = CredentialReportDisplay(
      new DateTime(2021,10,7,14, 58),
      Seq.empty,
      Seq(humanUser3, humanUser4)
    )
    val awsAccount2 = AwsAccount("aws-account-2", "aws account 2", "", "54321")
    val credentialReportDisplay3 = CredentialReportDisplay(
      new DateTime(2021,10,7,14, 58),
      Seq.empty,
      Seq(humanUser5, humanUser6)
    )
    val awsAccount3 = AwsAccount("aws-account-3", "aws account 3", "", "67890")
    val accountsCredsReport = List(
      (awsAccount1, credentialReportDisplay1),
      (awsAccount2, credentialReportDisplay2),
      (awsAccount3, credentialReportDisplay3)
    )

    "returns empty list when no accounts allowed in config" in {
      val result = unrecognisedUsersForAllowedAccounts(accountsCredsReport, allJanusUsernames, Nil)
      result shouldEqual Nil
    }
    "does not include an account that is not allowed in config" in {
      val result = unrecognisedUsersForAllowedAccounts(accountsCredsReport, allJanusUsernames, List(awsAccount1.id, awsAccount3.id))
      result.map(_._1.id) should not contain awsAccount2.id
    }
    "does not include multiple accounts that are not allowed in config" in {
      val result = unrecognisedUsersForAllowedAccounts(accountsCredsReport, allJanusUsernames, List(awsAccount1.id))
      result.map(_._1.id) should ((not contain awsAccount2.id) and (not contain awsAccount3.id))
    }
    "return list includes an allowed account" in {
      val result = unrecognisedUsersForAllowedAccounts(accountsCredsReport, allJanusUsernames, List(awsAccount2.id))
      result.map(_._1.id) should contain(awsAccount2.id)
    }
    // Permanent IAM credentials that have tags, which are not in the list of janus usernames should be returned
    // from unrecognisedUsersForAllowedAccounts as candidates for disablement.
    // The list of janus usernames is Nil, so all permanent IAM credentials should be returned from the function.
    "return list includes users for multiple allowed accounts" in {
      val result = unrecognisedUsersForAllowedAccounts(accountsCredsReport, Nil, List(awsAccount2.id, awsAccount3.id))
      result.flatMap(_._2.map(_.username)) should (contain (humanUser6.username) and contain (humanUser4.username))
    }
    "returns all data if all accounts allowed" in {
      val result = unrecognisedUsersForAllowedAccounts(accountsCredsReport, Nil, List(awsAccount1.id, awsAccount2.id, awsAccount3.id))
      result.flatMap(_._2.map(_.username)) should have length allJanusUsernames.length
    }
    "return list contains an IAM user that is not in the list of janus users" in {
      val result = unrecognisedUsersForAllowedAccounts(accountsCredsReport, List(humanUser2.username, humanUser3.username), List(awsAccount1.id))
      result.flatMap(_._2.map(_.username)) should contain (humanUser1.username)
    }
    "return empty list if all users are janus users" in {
      val result = unrecognisedUsersForAllowedAccounts(accountsCredsReport, allJanusUsernames, List(awsAccount1.id, awsAccount2.id, awsAccount3.id))
      result.flatMap(_._2.map(_.username)) shouldEqual Nil
    }
  }
  "makeFile" - {
    "creates a file with the correct contents" in {
      val file = makeFile("Hello World!")
      val source = Source.fromFile(file)
      val output = try source.mkString finally source.close()
      output shouldEqual "Hello World!"
    }
  }
}
