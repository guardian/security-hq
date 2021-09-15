package schedule

import com.gu.anghammarad.models.Stack
import model._
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}
import schedule.IamUsersToDisable.usersToDisable

class IamUsersToDisableTest extends FreeSpec with Matchers {

  private val dynamo: DynamoAlertService = new MockDynamoAlertService()

  private val account = AwsAccount("accountId","accountName","accountRole","accountNumber")
  def flaggedUserWithUsername(username: String) = Map(
     account -> Seq(
      IAMAlertTargetGroup(
        List(Stack("test-stack")),
        Seq(
          VulnerableUser(
            username = username,
            humanUser = true,
            tags = List.empty,
            disableDeadline = None
  )))))

  "usersToDisable" - {
    "returns the user when the deadline is today" in {
      val deadline = DateTime.now
      val today = deadline
      val username = "username1"

      dynamo.putAlert(IamAuditUser(
        "id",
        account.name,
        username,
        List(IamAuditAlert(
          VulnerableCredential,
          today,
          deadline
        ))
      ))

      val users = usersToDisable(flaggedUserWithUsername(username), dynamo, today)
      users.size shouldBe 1
      users.map { case (_, vulnerableUsers) =>
        vulnerableUsers shouldBe List(VulnerableUser(
          username = username,
          humanUser = true,
          tags = List.empty,
          disableDeadline = None))
      }
    }

    "does not return a user when the deadline is before today" in {
      val now = DateTime.now
      val deadline = now.minusDays(1)
      val today = now
      val username = "username2"

      dynamo.putAlert(IamAuditUser(
        "id",
        account.name,
        username,
        List(IamAuditAlert(
          VulnerableCredential,
          today,
          deadline
        ))
      ))

      val users = usersToDisable(flaggedUserWithUsername(username), dynamo, today)
      users(account) shouldBe empty
    }

    "does not return a user when the deadline is after today" in {
      val now = DateTime.now
      val deadline = now.plusDays(1)
      val today = now
      val username = "username3"

      dynamo.putAlert(IamAuditUser(
        "id",
        account.name,
        username,
        List(IamAuditAlert(
          VulnerableCredential,
          today,
          deadline
        ))
      ))

      val users = usersToDisable(flaggedUserWithUsername(username), dynamo, today)
      users(account) shouldBe empty
    }
  }
}
