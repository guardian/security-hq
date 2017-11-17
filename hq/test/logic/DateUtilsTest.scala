package logic

import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.{FreeSpec, Matchers}

class DateUtilsTest extends FreeSpec with Matchers {

  "datetutils" - {

    "calculate day difference" in {
      val date = DateTime.now(DateTimeZone.UTC).minusDays(3)
      DateUtils.dayDiff(Some(date)) shouldBe Some(3)
    }

    "print date in yyyy-MM-dd HH:mm:ss format" in {
      val date = new DateTime(2000, 1, 2, 3, 4, 5)
     DateUtils.printTime(date) shouldBe "03:04"
    }
  }
}
