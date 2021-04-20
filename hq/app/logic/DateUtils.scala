package logic

import org.joda.time.{DateTime, DateTimeZone, Duration}
import org.joda.time.format.ISODateTimeFormat

object DateUtils {
  val formatter = ISODateTimeFormat.dateTimeParser()
  val isoDateTimeParser = ISODateTimeFormat.dateTimeParser().withZoneUTC()

  def fromISOString(dateTime: String): DateTime = {
    formatter.parseDateTime(dateTime)
  }
  def toISOString(dateTime: DateTime) = ISODateTimeFormat.dateTime().print(dateTime)

  def dayDiff(date: Option[DateTime]): Option[Long] =  date.map(dayDiff)

  def dayDiff(date: DateTime): Long =  new Duration(date, DateTime.now(DateTimeZone.UTC)).getStandardDays

  def printTime(date: DateTime): String = date.toString("HH:mm")
}
