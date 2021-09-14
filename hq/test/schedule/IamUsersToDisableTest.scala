package schedule

import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}
import schedule.IamUsersToDisable.toDisableToday

class IamUsersToDisableTest extends FreeSpec with Matchers {
  "toDisableToday" - {
    "returns true when the deadline is today" in {
      val deadline = DateTime.now
      val today = DateTime.now
      toDisableToday(deadline, today) shouldBe true
    }
    "returns false when the deadline is not today" in {
      val deadline = DateTime.now.minusDays(1)
      toDisableToday(deadline) shouldBe false
    }
  }
}
