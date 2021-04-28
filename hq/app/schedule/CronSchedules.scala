package schedule

import model.CronSchedule

//a helpful quartz cron generator: https://www.freeformatter.com/cron-expression-generator-quartz.html
object CronSchedules {
  val firstMondayOfEveryMonth = CronSchedule("0 0 8 ? * 2#1", "At 8am, on the 1st Monday of the month, every month")
}
