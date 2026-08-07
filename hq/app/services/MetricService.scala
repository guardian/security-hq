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
    lifecycle: ApplicationLifecycle,
    environment: Environment,
    cacheService: CacheService
) extends Scheduler {

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
  def postCachedContentsAsMetrics()(using ExecutionContext): Unit = {
    val allExposedKeys = cacheService.getAllExposedKeys
    val allPublicBuckets = cacheService.getAllPublicBuckets
    val allCredentials = cacheService.getAllCredentials

    val (allExposedKeysFailures, allExposedKeysSuccesses) =
      allExposedKeys.toSeq.partitionMap {
        case (account, Right(value)) => Right((account, value))
        case (_, Left(err))          => Left(err)
      }

    val (allPublicBucketsFailures, allPublicBucketsSuccesses) =
      allPublicBuckets.toSeq.partitionMap {
        case (account, Right(value)) => Right((account, value))
        case (_, Left(err))          => Left(err)
      }

    val (allCredentialsFailures, allCredentialsSuccesses) =
      allCredentials.toSeq.partitionMap {
        case (account, Right(value)) => Right((account, value))
        case (_, Left(err))          => Left(err)
      }

    if (allExposedKeysFailures.nonEmpty || allPublicBucketsFailures.nonEmpty || allCredentialsFailures.nonEmpty) {
      logger.warn(
        s"Skipping cloudwatch metrics update as some data is missing from the cache: $allExposedKeysFailures, $allPublicBucketsFailures, $allCredentialsFailures"
      )
    } else {
      logger.info("Posting new metrics to cloudwatch")
      Cloudwatch.logExposedKeysMetric(allExposedKeysSuccesses.toMap)
      Cloudwatch.logS3TotalMetric(allPublicBucketsSuccesses.toMap)
      Cloudwatch.logMetricsForCredentialsReport(allCredentialsSuccesses.toMap)
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
        implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
        postCachedContentsAsMetrics()
      }

    lifecycle.addStopHook(cloudwatchSubscription)
  }
}
