package schedule

import com.gu.anghammarad.models.Stack
import model.{Tag, VulnerableUser}
import org.scalatest.{FreeSpec, Matchers}
import schedule.IamTargetGroups.getNotificationTargetGroups

class IamTargetGroupsTest extends FreeSpec with Matchers {
  private val outdatedUser1 = VulnerableUser("lesleyKnope", humanUser = true, tags = List())
  private val outdatedUser2 = VulnerableUser("ronSwanson", humanUser = true, tags = List())
  private val outdatedUser3 = VulnerableUser("tomHaverford", humanUser = true, tags = List())

  private val noMfaUser1 = VulnerableUser("april", humanUser = true, tags = List())
  private val noMfaUser2 = VulnerableUser("andy", humanUser = true, tags = List())

  "getNotificationTargetGroups" - {
    "when users have no tags, builds a single target group with no target" in {
      val users = Seq(outdatedUser1, outdatedUser2, noMfaUser1, noMfaUser2)
      val targetGroups = getNotificationTargetGroups(users)
      targetGroups.length shouldBe 1
      targetGroups.head.targets shouldBe List.empty
      targetGroups.head.users shouldBe users
    }

    "when users have tags, builds target groups for each combination of tags" in {
      val parks = Tag("Stack", "parks")
      val recreation = Tag("Stack", "recreation")

      val users = Seq(
        outdatedUser1,
        outdatedUser2.copy(tags = List(parks)),
        outdatedUser3.copy(tags = List(recreation))
      )

      val targetGroups = getNotificationTargetGroups(users)

      withClue(s"targetGroups: $targetGroups should contain 3 items") { targetGroups.length shouldBe 3 }

      val parkGroup = targetGroups.find(t => t.targets.exists{
        case Stack(stack) => stack == "parks"
        case _ => false
      })
      withClue(s"targetGroups: $targetGroups should contain a target with stack 'parks'") { parkGroup shouldBe defined }
      val parkGroupUsers = parkGroup.get.users
      parkGroupUsers.head.username shouldBe outdatedUser2.username

      val noTagGroup = targetGroups.find(t => t.targets.isEmpty)
      withClue(s"targetGroups: $targetGroups should contain an empty target") { noTagGroup shouldBe defined }
      noTagGroup.get.users shouldBe List(outdatedUser1)

    }
  }
}
