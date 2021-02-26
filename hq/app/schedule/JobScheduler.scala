package schedule

import org.quartz.CronScheduleBuilder.cronSchedule
import org.quartz.JobBuilder.newJob
import org.quartz.{JobDataMap, Scheduler}
import org.quartz.TriggerBuilder.newTrigger

class JobScheduler(scheduler: Scheduler, adminJobs: List[JobRunner]) {

  def initialise(): Unit = {
    adminJobs.foreach(job => scheduleJob(job))
  }

  private def scheduleJob(job: JobRunner): Unit = {
    val jobDetail = newJob(classOf[JobWrapper])
      .withIdentity(job.id)
      .usingJobData(buildJobDataMap(job))
      .build()
    val trigger = newTrigger()
      .withIdentity(job.triggerKey)
      .withSchedule(cronSchedule(job.cronSchedule.cron))
      .build()
    scheduler.scheduleJob(jobDetail, trigger)
  }

  private def buildJobDataMap(job: JobRunner): JobDataMap = {
    val jobDataMap = new JobDataMap()
    jobDataMap.put(AdminJobScheduler.JobDataKeys.JobId, job.id)
    jobDataMap.put(AdminJobScheduler.JobDataKeys.Runner, job)
    jobDataMap
  }

  def start(): Unit = scheduler.start()

  def shutdown(): Unit = scheduler.shutdown()
}

object AdminJobScheduler {

  object JobDataKeys {
    val JobId = "JobId"
    val Runner = "runner"
  }
}
