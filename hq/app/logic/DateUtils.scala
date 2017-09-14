package logic

import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}

object DateUtils {
  val formatter = ISODateTimeFormat.dateTimeParser()

  def fromISOString(dateTime: String): DateTime = {
    formatter.parseDateTime(dateTime)
  }
}
