package schedule

import model.CronSchedule

//TODO consider if we need all of these
object CronSchedules {
  val onceADayAt1am = CronSchedule("0 0 1 * * ?", "Run once a day at 1am")
  val onceADayAt2am = CronSchedule("0 0 2 * * ?", "Run once a day at 2am")
  val onceADayAt3am = CronSchedule("0 0 3 * * ?", "Run once a day at 3am")
  val onceADayAt4am = CronSchedule("0 0 4 * * ?", "Run once a day at 4am")
  val onceADayAt5am = CronSchedule("0 0 5 * * ?", "Run once a day at 6am")
  val onceADayAt6am = CronSchedule("0 0 6 * * ?", "Run once a day at 6am")
  val onceADayAt7am = CronSchedule("0 0 7 * * ?", "Run once a day at 7am")
  val onceADayAt8am = CronSchedule("0 0 8 * * ?", "Run once a day at 8am")
  val onceADayAt9am = CronSchedule("0 0 9 * * ?", "Run once a day at 9am")
}
