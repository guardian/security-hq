package logic

import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime, DateTimeZone, Duration}
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}

object DateUtils extends LazyLogging {
  val isoDateTimeParser = ISODateTimeFormat.dateTimeParser().withZoneUTC()

  def dayDiff(date: Option[DateTime]): Option[Long] = date.map(dayDiff)

  def dayDiff(date: DateTime): Long = new Duration(date, DateTime.now(DateTimeZone.UTC)).getStandardDays

  def printDay(day: DateTime): String = DateTimeFormat.forPattern("dd/MM/yyyy").print(day)
}
