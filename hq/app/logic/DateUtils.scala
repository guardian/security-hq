package logic

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

object DateUtils {
  val formatter = ISODateTimeFormat.dateTimeParser()
  val isoDateTimeParser = ISODateTimeFormat.dateTimeParser().withZoneUTC()

  def fromISOString(dateTime: String): DateTime = {
    formatter.parseDateTime(dateTime)
  }
}
