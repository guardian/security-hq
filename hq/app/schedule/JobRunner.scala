package schedule

import model.CronSchedule
import org.quartz.{JobKey, TriggerKey}

abstract class JobRunner {
  val id: String
  val description: String
  val cronSchedule: CronSchedule

  val name: String = getClass.getName

  val jobKey = new JobKey(name)
  val triggerKey = new TriggerKey(name)

  def run(testMode: Boolean = false): Unit
}