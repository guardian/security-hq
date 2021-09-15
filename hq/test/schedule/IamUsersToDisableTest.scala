package schedule

import com.gu.anghammarad.models.Stack
import model.{AwsAccount, IAMAlertTargetGroup, VulnerableUser}
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}
import schedule.IamUsersToDisable.toDisableToday

class IamUsersToDisableTest extends FreeSpec with Matchers {

  val dynamo: DynamoAlertService = new MockDynamoAlertService()

  def flaggedUsersWithDeadline(deadline: Option[DateTime]) = Map(
    AwsAccount("","","","") -> Seq(
      IAMAlertTargetGroup(
        List(Stack("test-stack")),
        Seq(
          VulnerableUser(
            username = "username",
            humanUser = true,
            tags = List.empty,
            disableDeadline = deadline
  )))))

  "usersToDisable" - {
    "returns the user when the deadline is today" in {
      val deadline = DateTime.now
      val today = deadline

      val users = usersToDisable(flaggedUsersWithDeadline(Some(deadline)), dynamo, today)
      users.size shouldBe 1
      users.map { case (_, vulnerableUsers) =>
        vulnerableUsers shouldBe VulnerableUser(
          username = "username",
          humanUser = true,
          tags = List.empty,
          disableDeadline = Some(deadline))
      }
    }

    "does not return a user when the deadline is not today" in {
      val now = DateTime.now
      val deadline = now.minusDays(1)
      val today = now

      val users = usersToDisable(flaggedUsersWithDeadline(Some(deadline)), dynamo, today)
      users shouldBe empty
    }
  }
}
