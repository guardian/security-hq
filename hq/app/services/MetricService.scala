package services

import logging.Cloudwatch
import model.*
import play.api.*
import play.api.inject.ApplicationLifecycle
import utils.Scheduler
import utils.attempt.FailedAttempt

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

class MetricService(
    config: Configuration,
    lifecycle: ApplicationLifecycle,
    environment: Environment,
    cacheService: CacheService
)(implicit ec: ExecutionContext)
    extends Scheduler {

  def collectFailures[T](
      list: List[Map[AwsAccount, Either[FailedAttempt, T]]]
  ): List[(AwsAccount, FailedAttempt)] = {
    list.flatMap { dataMap =>
      dataMap.toSeq.collect { case (account, Left(failedAttempt)) =>
        (account, failedAttempt)
      }
    }
  }

  /*
   * The intended behaviour for this method is to only log data to cloudwatch if cache service has a full
   * data set. If any of it is missing, we try again in 6 hours.
   *
   * This is counter intuitive. All the different datapoints (security groups etc)
   * are independent of each other, so it follows that we'd track them independently, and one being missing
   * shouldn't affect the other.
   *
   * The reasoning to force them to be coupled to each other and taking this all or nothing approach is that
   * it makes aggregation and calculating SUMs over time much easier.
   *
   * See these 2 PRs for further discussion and examples with data
   * - https://github.com/guardian/security-hq/pull/211
   * - https://github.com/guardian/security-hq/pull/245#discussion_r632548991
   */
  def postCachedContentsAsMetrics(): Unit = {
    val allExposedKeys = cacheService.getAllExposedKeys
    val allPublicBuckets = cacheService.getAllPublicBuckets
    val allCredentials = cacheService.getAllCredentials

    val failures = collectFailures(
      List(allExposedKeys, allPublicBuckets, allCredentials)
    )

    if (failures.nonEmpty) {
      // Limit the summary to the first few failures to avoid overly large log
      // lines when a widespread cache issue affects many accounts at once.
      val maxFailuresInSummary = 5
      val summary = failures.take(maxFailuresInSummary).map { case (account, failedAttempt) =>
        s"${account.name}: ${failedAttempt.logMessage}"
      }.mkString(", ")

      val omitted = failures.size - maxFailuresInSummary
      val summaryWithCount =
        if (omitted > 0) s"$summary (and $omitted more; ${failures.size} failures in total)"
        else summary

      val logMessage =
        s"Skipping cloudwatch metrics update as some data is missing from the cache: $summaryWithCount"

      // Surface an example underlying exception so its stack trace is logged.
      // Use a lazy iterator with collectFirst so we stop at the first available
      // exception rather than traversing and allocating for every failure.
      failures.iterator
        .flatMap { case (_, failedAttempt) => failedAttempt.firstException }
        .nextOption() match {
        case Some(exampleCause) =>
          logger.warn(s"$logMessage - see stacktrace for an example cause", exampleCause)
        case None =>
          logger.warn(logMessage)
      }
    } else {
      logger.info("Posting new metrics to cloudwatch")
      Cloudwatch.logAsMetric(allExposedKeys, Cloudwatch.DataType.iamKeysTotal)
      Cloudwatch.logAsMetric(allPublicBuckets, Cloudwatch.DataType.s3Total)
      Cloudwatch.logMetricsForCredentialsReport(allCredentials)
    }
  }

  if (environment.mode != Mode.Test) {
    val initialDelay =
      if (environment.mode == Mode.Prod) 15.minutes
      else Duration.Zero

    val cloudwatchSubscription =
      scheduleAtFixedRate(
        initialDelay = initialDelay,
        interval = 6.hours
      ) { () =>
        postCachedContentsAsMetrics()
      }

    lifecycle.addStopHook(cloudwatchSubscription)
  }
}
