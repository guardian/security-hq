package schedule.unrecognised

import com.gu.janus
import com.gu.janus.model.{ACL, JanusData, SupportACL}
import logic.IamUnrecognisedUsers._
import model._
import org.joda.time.DateTime
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import utils.attempt.{FailedAttempt, Failure}

import java.time.Duration
import scala.io.Source

class IamUnrecognisedUsersTest extends AnyFreeSpec with Matchers {
  val humanUser1 = HumanUser("ade.bimbola", true, AccessKey(NoKey, None), AccessKey(NoKey, None), Green, None, None, List(Tag(USERNAME_TAG_KEY, "ade.bimbola")))
  val humanUser2 = humanUser1.copy(username = "john.akindele", tags = List(Tag(USERNAME_TAG_KEY, "john.akindele")))
  val humanUser3 = humanUser1.copy(username = "khadija.omodara", tags = List(Tag(USERNAME_TAG_KEY, "khadija.omodara")))
  val humanUser4 = humanUser1.copy(username = "nneka.obi", tags = List(Tag(USERNAME_TAG_KEY, "nneka.obi")))
  val humanUser5 = humanUser1.copy(username = "john.doe", tags = List())
  val humanUser6 = humanUser1.copy(username = "jane.fonda", tags = List(Tag(USERNAME_TAG_KEY, "jane.fonda")))
  val humanUser7 = humanUser1.copy(username = "amina.adewusi", tags = List(Tag(USERNAME_TAG_KEY, "amina.adewusi")))
  val allJanusUsernames = List("ade.bimbola", "john.akindele", "khadija.omodara", "nneka.obi", "jane.fonda")

  "findUnrecognisedIamUsers" - {
    "output janus username from JanusData type" in {
      val dummyJanusData = JanusData(
        Set(janus.model.AwsAccount("Deploy Tools", "deployTools")),
        ACL(Map("firstName.secondName" -> Set.empty)),
        ACL(Map.empty),
        SupportACL.create(Map.empty, Set.empty),
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
    val awsAccount3 = AwsAccount("aws-account-3", "aws account 3", "", "01234")
    val credentialReportDisplay4 = CredentialReportDisplay(
      new DateTime(2021,10,7,14, 58),
      Seq.empty,
      Seq.empty
    )
    val awsAccount4 = AwsAccount("aws-account-4", "aws account 4", "", "67890")
    val credentialReportDisplay5 = CredentialReportDisplay(
      new DateTime(2021,10,7,14, 58),
      Seq.empty,
      Seq(humanUser7)
    )
    val awsAccount5 = AwsAccount("aws-account-5", "aws account 5", "", "02135")
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
      result.map { case AccountUnrecognisedUsers(account, _) => account.id} should not contain awsAccount2.id
    }
    "does not include multiple accounts that are not allowed in config" in {
      val result = unrecognisedUsersForAllowedAccounts(accountsCredsReport, allJanusUsernames, List(awsAccount1.id))
      result.map { case AccountUnrecognisedUsers(account, _) => account.id} should ((not contain awsAccount2.id) and (not contain awsAccount3.id))
    }
    // Ensure that no accounts are returned with an empty list of vulnerable users. This is important for the rest of the logic in the job.
    "does not return an account with an empty list of vulnerable users" in {
      val result = unrecognisedUsersForAllowedAccounts(List((awsAccount4, credentialReportDisplay4)), allJanusUsernames, List(awsAccount4.id))
      result shouldEqual Nil
    }
    "return list includes an allowed account" in {
      val result = unrecognisedUsersForAllowedAccounts(List((awsAccount5, credentialReportDisplay5)), allJanusUsernames, List(awsAccount5.id))
      result.map { case AccountUnrecognisedUsers(account, _) => account.id} should contain(awsAccount5.id)
    }
    // Permanent IAM credentials that have tags, which are not in the list of janus usernames should be returned
    // from unrecognisedUsersForAllowedAccounts as candidates for disablement.
    // The list of janus usernames is Nil, so all permanent IAM credentials should be returned from the function.
    "return list includes users for multiple allowed accounts" in {
      val result = unrecognisedUsersForAllowedAccounts(accountsCredsReport, Nil, List(awsAccount2.id, awsAccount3.id))
      val returnedUsernames = result.flatMap { case AccountUnrecognisedUsers(_, users) => users.map(_.username) }
      returnedUsernames should (contain (humanUser6.username) and contain (humanUser4.username))
    }
    "returns all data if all accounts allowed" in {
      val result = unrecognisedUsersForAllowedAccounts(accountsCredsReport, Nil, List(awsAccount1.id, awsAccount2.id, awsAccount3.id))
      val returnedUsernames = result.flatMap { case AccountUnrecognisedUsers(_, users) => users.map(_.username) }
      returnedUsernames should have length allJanusUsernames.length
    }
    "return list contains an IAM user that is not in the list of janus users" in {
      val result = unrecognisedUsersForAllowedAccounts(accountsCredsReport, List(humanUser2.username, humanUser3.username), List(awsAccount1.id))
      val returnedUsernames = result.flatMap { case AccountUnrecognisedUsers(_, users) => users.map(_.username) }
      returnedUsernames should contain (humanUser1.username)
    }
    "return empty list if all users are janus users" in {
      val result = unrecognisedUsersForAllowedAccounts(accountsCredsReport, allJanusUsernames, List(awsAccount1.id, awsAccount2.id, awsAccount3.id))
      val returnedUsernames = result.flatMap { case AccountUnrecognisedUsers(_, users) => users.map(_.username) }
      returnedUsernames shouldEqual Nil
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

  "isTaggedForUnrecognisedUser" - {
    "returns true when list of tags contains unrecognised user tag key and the value is not an empty string and contains a fullstop" in {
      val tags = List(Tag(USERNAME_TAG_KEY, "amina.adewusi"), Tag("test", "test"))
      isTaggedForUnrecognisedUser(tags) shouldBe true
    }
    "returns false when list of tags is empty" in {
      isTaggedForUnrecognisedUser(Nil) shouldBe false
    }
    "returns false when list of tags does not contain unrecognised user tag" in {
      val tags = List(Tag("test", "test"), Tag("testAgain", "amina.adewusi"))
      isTaggedForUnrecognisedUser(tags) shouldBe false
    }
    "returns false when unrecognised user tag value does not contain full stop" in {
      val tags = List(Tag(USERNAME_TAG_KEY, "aminaadewusi"))
      isTaggedForUnrecognisedUser(tags) shouldBe false
    }
    "returns false when when unrecognised user tag value is an empty string" in {
      val tags = List(Tag(USERNAME_TAG_KEY, ""))
      isTaggedForUnrecognisedUser(tags) shouldBe false
    }
  }
}
