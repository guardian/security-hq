package schedule

import org.quartz.{Job, JobExecutionContext}
import play.api.Logging

class JobWrapper extends Job with Logging {

  def execute(context: JobExecutionContext): Unit = {
    import AdminJobScheduler.JobDataKeys
    val jobData = context.getJobDetail.getJobDataMap
    val job = jobData.get(JobDataKeys.Runner).asInstanceOf[JobRunner]

    logger.info(s"Running jobId=${job.id} - ${job.description}")
    job.run()
  }
}
