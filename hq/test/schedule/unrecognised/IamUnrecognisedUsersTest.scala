package schedule.unrecognised

import com.gu.janus.model.{ACL, AwsAccount, JanusData, SupportACL}
import model._
import org.joda.time.Seconds
import org.scalatest.{FreeSpec, Matchers}
import schedule.unrecognised.IamUnrecognisedUsers.{USERNAME_TAG_KEY, filterUnrecognisedIamUsers, getCredsReportDisplayForAccount, getJanusUsernames}
import utils.attempt.{FailedAttempt, Failure}

class IamUnrecognisedUsersTest extends FreeSpec with Matchers {
  "findUnrecognisedIamUsers" - {
    val humanUser1 = HumanUser("", true, AccessKey(NoKey, None), AccessKey(NoKey, None), Green, None, None, List(Tag(USERNAME_TAG_KEY, "ade.bimbola")))
    val humanUser2 = humanUser1.copy(tags = List(Tag(USERNAME_TAG_KEY, "john.akindele")))
    val humanUser3 = humanUser1.copy(tags = List(Tag(USERNAME_TAG_KEY, "khadija.omodara")))
    val humanUser4 = humanUser1.copy(tags = List(Tag(USERNAME_TAG_KEY, "nneka.obi")))
    val humanUser5 = humanUser1.copy(tags = List())

    "get janus usernames" in {
      val dummyJanusData = JanusData(
        Set(AwsAccount("Deploy Tools", "deployTools")),
        ACL(Map("firstName.secondName" -> Set.empty)),
        ACL(Map.empty),
        SupportACL(Map.empty, Set.empty, Seconds.ZERO),
        None
      )

      getJanusUsernames(dummyJanusData) shouldEqual List("firstName.secondName")
    }
    "get unrecognised human users" in {
      val permanentIamUsers: Seq[HumanUser] = List(humanUser1, humanUser2, humanUser3, humanUser4, humanUser5)
      val vulnerableUsers: Seq[VulnerableUser] = List(VulnerableUser.fromIamUser(humanUser4))
      val janusUsers: Seq[String] = List("ade.bimbola", "john.akindele", "khadija.omodara")
      filterUnrecognisedIamUsers(permanentIamUsers, janusUsers) shouldEqual vulnerableUsers
    }
  }

  "getCredsReportDisplayForAccount" - {
    val failedAttempt: FailedAttempt = FailedAttempt(Failure("error", "error", 500))

    "if an either is a left, aws account does not appear in response" in {
      val accountCredsLeft = Map(1 -> Left(failedAttempt))
      getCredsReportDisplayForAccount(accountCredsLeft) shouldEqual Nil
    }
    "if an either is a right, aws account included in response" in {
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
}
