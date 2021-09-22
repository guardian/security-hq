package schedule.vulnerable

import model.{IamAuditAlert, VulnerableCredential}
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}
import schedule.vulnerable.IamDeadline.{getNearestDeadline, isFinalAlert, isWarningAlert}

class IamDeadlineTest extends FreeSpec with Matchers {
  "isWarningAlert" - {
    "returns true when the deadline is one week away" in {
      val deadline = DateTime.now.plusWeeks(1)
      val today = DateTime.now
      isWarningAlert(deadline, today) shouldBe true
    }
    "returns true when the deadline is three weeks away" in {
      val deadline = DateTime.now.plusWeeks(3)
      val today = DateTime.now
      isWarningAlert(deadline, today) shouldBe true
    }
    "returns false when the deadline is not one or three weeks away" in {
      val deadline = DateTime.now.plusWeeks(2)
      val today = DateTime.now
      isWarningAlert(deadline, today) shouldBe false
    }
  }

  "isFinalAlert" - {
    "returns true when the deadline is the next day" in {
      val deadline = DateTime.now.plusDays(1)
      val today = DateTime.now
      isFinalAlert(deadline, today) shouldBe true
    }
    "returns false when the deadline is not the next day" in {
      val deadline = DateTime.now.plusDays(2)
      val today = DateTime.now
      isFinalAlert(deadline, today) shouldBe false
    }
  }

  "getNearestDeadline" - {
    "for two alerts in the future returns nearest deadline" in {
      val nearestDeadline = DateTime.now.plusDays(1).withTimeAtStartOfDay
      val alert1 = IamAuditAlert(VulnerableCredential, DateTime.now.minusWeeks(3), nearestDeadline)
      val alert2 = IamAuditAlert(VulnerableCredential, DateTime.now.minusWeeks(3), DateTime.now.plusDays(2).withTimeAtStartOfDay)
      getNearestDeadline(List(alert1, alert2)) shouldEqual nearestDeadline
    }

    "for two alerts, one in the past, returns nearest (future) deadline" in {
      val nearestDeadline = DateTime.now.plusDays(10).withTimeAtStartOfDay
      val alert1 = IamAuditAlert(VulnerableCredential, DateTime.now.minusWeeks(3), nearestDeadline)
      val alert2 = IamAuditAlert(VulnerableCredential, DateTime.now.minusWeeks(3), DateTime.now.minusDays(1).withTimeAtStartOfDay)
      getNearestDeadline(List(alert1, alert2)) shouldEqual nearestDeadline
    }

    "for two alerts, one today, returns nearest deadline" in {
      val nearestDeadline = DateTime.now.withTimeAtStartOfDay
      val alert1 = IamAuditAlert(VulnerableCredential, DateTime.now.minusWeeks(3), nearestDeadline)
      val alert2 = IamAuditAlert(VulnerableCredential, DateTime.now.minusWeeks(3), DateTime.now.plusDays(1).withTimeAtStartOfDay)
      getNearestDeadline(List(alert1, alert2)) shouldEqual nearestDeadline
    }
  }
}
