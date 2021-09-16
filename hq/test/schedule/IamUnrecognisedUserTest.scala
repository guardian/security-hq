package schedule

import com.gu.janus.model.{ACL, AwsAccount, JanusData, SupportACL}
import model._
import org.joda.time.{DateTime, Seconds}
import org.scalatest.{FreeSpec, Matchers}
import schedule.unrecognised.IamUnrecognisedUsers.{filterUnrecognisedIamUsers, getJanusUsernames}

class IamUnrecognisedUserTest extends FreeSpec with Matchers {
  val humanUser1 = HumanUser("", true, AccessKey(NoKey, None), AccessKey(NoKey, None), Green, None, None, List(Tag("name", "ade.bimbola")))
  val humanUser2 = humanUser1.copy(tags = List(Tag("name", "john.akindele")))
  val humanUser3 = humanUser1.copy(tags = List(Tag("name", "khadija.omodara")))
  val humanUser4 = humanUser1.copy(tags = List(Tag("name", "nneka.obi")))

  val credsReportDisplay = CredentialReportDisplay(
    DateTime.now,
    Seq.empty,
    Seq(humanUser1, humanUser2, humanUser3, humanUser4)
  )

  "findUnrecognisedIamUsers" - {
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
      val permanentIamUsers: Seq[HumanUser] = List(humanUser1, humanUser2, humanUser3, humanUser4)
      val vulnerableUsers: Seq[VulnerableUser] = List(VulnerableUser.fromIamUser(humanUser4))
      val janusUsers: Seq[String] = List("ade.bimbola", "john.akindele", "khadija.omodara")
      filterUnrecognisedIamUsers(permanentIamUsers, janusUsers) shouldEqual vulnerableUsers
    }
  }
}
