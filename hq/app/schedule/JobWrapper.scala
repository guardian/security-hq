package schedule

import org.quartz.{Job, JobExecutionContext}

class JobWrapper extends Job {

  def execute(context: JobExecutionContext): Unit = {
    import AdminJobScheduler.JobDataKeys
    val jobData = context.getJobDetail.getJobDataMap
    val job = jobData.get(JobDataKeys.Runner).asInstanceOf[JobRunner]

    //TODO: Log.info(s"Running jobId=${job.id} - ${job.description} ...")
    job.run()
  }
}
