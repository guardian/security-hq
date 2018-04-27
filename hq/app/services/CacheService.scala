package services

import aws.ec2.EC2
import aws.iam.IAMClient
import aws.inspector.Inspector
import aws.support.TrustedAdvisorExposedIAMKeys
import com.amazonaws.regions.Regions
import com.amazonaws.services.cloudformation.AmazonCloudFormationAsync
import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.inspector.AmazonInspectorAsync
import com.amazonaws.services.support.AWSSupportAsync
import com.gu.Box
import config.Config
import model._
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Environment, Logger, Mode}
import utils.attempt.{FailedAttempt, Failure}
import org.quartz._
import org.quartz.impl.StdSchedulerFactory
import org.quartz.spi.{JobFactory, TriggerFiredBundle}

import scala.concurrent.ExecutionContext

class CacheService(
    config: Configuration,
    lifecycle: ApplicationLifecycle,
    environment: Environment,
    inspectorClients: Map[(String, Regions), AmazonInspectorAsync],
    ec2Clients: Map[(String, Regions), AmazonEC2Async],
    cfnClients: Map[(String, Regions), AmazonCloudFormationAsync],
    taClients: Map[(String, Regions), AWSSupportAsync],
    iamClients: Map[(String, Regions),  AmazonIdentityManagementAsync]
  )(implicit ec: ExecutionContext) extends JobFactory {
  private val accounts = Config.getAwsAccounts(config)
  private val startingCache = accounts.map(acc => (acc, Left(Failure.cacheServiceError(acc.id, "cache").attempt))).toMap
  private val credentialsBox: Box[Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]]] = Box(startingCache)
  private val exposedKeysBox: Box[Map[AwsAccount, Either[FailedAttempt, List[ExposedIAMKeyDetail]]]] = Box(startingCache)
  private val sgsBox: Box[Map[AwsAccount, Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]]]] = Box(startingCache)
  private val inspectorBox: Box[Map[AwsAccount, Either[FailedAttempt, List[InspectorAssessmentRun]]]] = Box(startingCache)

  def getAllCredentials: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = credentialsBox.get()

  def getCredentialsForAccount(awsAccount: AwsAccount): Either[FailedAttempt, CredentialReportDisplay] = {
    credentialsBox.get().getOrElse(
      awsAccount,
      Left(Failure.cacheServiceError(awsAccount.id, "credentials").attempt)
    )
  }

  def getAllExposedKeys: Map[AwsAccount, Either[FailedAttempt, List[ExposedIAMKeyDetail]]] = exposedKeysBox.get()

  def getExposedKeysForAccount(awsAccount: AwsAccount): Either[FailedAttempt, List[ExposedIAMKeyDetail]] = {
    exposedKeysBox.get().getOrElse(
      awsAccount,
      Left(Failure.cacheServiceError(awsAccount.id, "exposed keys").attempt)
    )
  }

  def getAllSgs: Map[AwsAccount, Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]]] = sgsBox.get()

  def getSgsForAccount(awsAccount: AwsAccount): Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]] = {
    sgsBox.get().getOrElse(
      awsAccount,
      Left(Failure.cacheServiceError(awsAccount.id, "security group").attempt)
    )
  }

  def getAllInspectorResults: Map[AwsAccount, Either[FailedAttempt, List[InspectorAssessmentRun]]] = inspectorBox.get()

  def getInspectorResultsForAccount(awsAccount: AwsAccount): Either[FailedAttempt, List[InspectorAssessmentRun]] = {
    inspectorBox.get().getOrElse(
      awsAccount,
      Left(Failure.cacheServiceError(awsAccount.id, "AWS Inspector results").attempt)
    )
  }

  private def refreshCredentialsBox(): Unit = {
    Logger.info("Started refresh of the Credentials data")
    for {
      allCredentialReports <- IAMClient.getAllCredentialReports(accounts, cfnClients, ec2Clients, iamClients)
    } yield {
      Logger.info("Sending the refreshed data to the Credentials Box")
      credentialsBox.send(allCredentialReports.toMap)
    }
  }

  private def refreshExposedKeysBox(): Unit = {
    Logger.info("Started refresh of the Exposed Keys data")
    for {
      allExposedKeys <- TrustedAdvisorExposedIAMKeys.getAllExposedKeys(accounts, taClients)
    } yield {
      Logger.info("Sending the refreshed data to the Exposed Keys Box")
      exposedKeysBox.send(allExposedKeys.toMap)
    }
  }

  private def refreshSgsBox(): Unit = {
    Logger.info("Started refresh of the Security Groups data")
    for {
      _ <- EC2.refreshSGSReports(accounts, taClients)
      allFlaggedSgs <- EC2.allFlaggedSgs(accounts, ec2Clients, taClients)
    } yield {
      Logger.info("Sending the refreshed data to the Security Groups Box")
      sgsBox.send(allFlaggedSgs.toMap)
    }
  }

  def refreshInspectorBox(): Unit = {
    Logger.info("Started refresh of the AWS Inspector data")
    for {
      allInspectorRuns <- Inspector.allInspectorRuns(accounts, inspectorClients)
    } yield {
      Logger.info("Sending the refreshed data to the AWS Inspector Box")
      inspectorBox.send(allInspectorRuns.toMap)
    }
  }

  if (environment.mode != Mode.Test) {
    // Frequent jobs are staggered over two minutes, giving one every 40 seconds
    setUpQuartzScheduleJob(Cache.Credentials, "0 0-59/2 * * * ?")
    setUpQuartzScheduleJob(Cache.ExposedKeys, "40 0-59/2 * * * ?")
    setUpQuartzScheduleJob(Cache.SecurityGroups, "20 1-59/2 * * * ?")
    // AWS Inspector Runs are only scheduled for the early hours of Mon & Thu at the time of writing, so
    // less frequent updates in Security HQ are fine.  7am is the earliest people are likely to be at work.
    setUpQuartzScheduleJob(Cache.AWSInspector, "45 20 1,7,13,19 * * ?")
  }

  private def setUpQuartzScheduleJob(cache: Cache.EnumVal, cronString: String) = {
    val jobKey = JobKey.jobKey(cache.toString, "refresh")
    val schedule = CronScheduleBuilder.cronSchedule(cronString)

    val jobDetail = JobBuilder
      .newJob(classOf[Job])
      .withIdentity(jobKey)
      .build()

    val trigger = TriggerBuilder
      .newTrigger()
      .withIdentity(s"cron-${cache.toString}")
      .withSchedule(schedule)
      .build()

    val scheduler = new StdSchedulerFactory().getScheduler()
    scheduler.setJobFactory(this)
    scheduler.scheduleJob(jobDetail, trigger)
    // Run once straight away, so that we don't have a delay on initial boot
    scheduler.triggerJob(jobKey)
    scheduler.start()
  }

  def refresh(cache: Cache.EnumVal): Unit = {
    cache match {
      case Cache.Credentials => refreshCredentialsBox()
      case Cache.AWSInspector => refreshInspectorBox()
      case Cache.ExposedKeys => refreshExposedKeysBox()
      case Cache.SecurityGroups => refreshSgsBox()
    }
  }

  // Turns this class into a scheduler job factory.
  override def newJob(bundle: TriggerFiredBundle, scheduler: Scheduler): Job =
    (context: JobExecutionContext) => refresh(Cache.find(context.getJobDetail.getKey.getName))

  object Cache {
    sealed trait EnumVal
    case object AWSInspector extends EnumVal
    case object Credentials extends EnumVal
    case object ExposedKeys extends EnumVal
    case object SecurityGroups extends EnumVal
    def find(name: String) = Set(AWSInspector, Credentials, ExposedKeys, SecurityGroups)
      .find(c => c.toString.equals(name)).get
  }
}
