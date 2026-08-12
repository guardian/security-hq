package logic

import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class DateUtilsTest extends AnyFreeSpec with Matchers {

  "datetutils" - {

    "calculate day difference" in {
      val date = DateTime.now(DateTimeZone.UTC).minusDays(3)
      DateUtils.dayDiff(Some(date)) shouldBe Some(3)
    }

  }
}
