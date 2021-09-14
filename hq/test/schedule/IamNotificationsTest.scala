package schedule

import model.AwsAccount
import org.scalatest.{FreeSpec, Matchers}
import schedule.IamNotifications.makeNotification

class IamNotificationsTest extends FreeSpec with Matchers {
  "makeNotification" - {
    "returns nothing when there are no old access keys or missing mfas" in {
      val allCreds = Map(AwsAccount("", "", "", "") -> Seq.empty)
      val result = List.empty
      makeNotification(allCreds) shouldEqual result
    }
  }
}
