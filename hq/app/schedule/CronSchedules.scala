package schedule

import model.CronSchedule

//a helpful quartz cron generator: https://www.freeformatter.com/cron-expression-generator-quartz.html
object CronSchedules {
  val everyWeekDay = CronSchedule("0 0 15 ? * MON-FRI *", "At 3pm, every week day")
}
