package schedule

import model.AwsAccount
import org.scalatest.{FreeSpec, Matchers}
import schedule.IamNotifications.makeNotifications

class IamNotificationsTest extends FreeSpec with Matchers {
  "makeNotification" - {
    "returns nothing when there are no old access keys or missing mfas" in {
      val allCreds = Map(AwsAccount("", "", "", "") -> Seq.empty)
      val result = List.empty
      makeNotifications(allCreds) shouldEqual result
    }
  }
}
