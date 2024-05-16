package services

import logging.Cloudwatch
import model._
import play.api._
import play.api.inject.ApplicationLifecycle
import rx.lang.scala.Observable
import utils.attempt.FailedAttempt

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


class MetricService(
    config: Configuration,
    lifecycle: ApplicationLifecycle,
    environment: Environment,
    cacheService: CacheService
  )(implicit ec: ExecutionContext) extends Logging {

  def collectFailures[T](list: List[Map[AwsAccount, Either[FailedAttempt, T]]]): List[(AwsAccount, FailedAttempt)] = {
    list.flatMap { dataMap =>
      dataMap.toSeq.collect {
        case (account, Left(failedAttempt)) => (account, failedAttempt)
      }
    }
  }

  def discardSuppressedSgs(sgsMap: Map[AwsAccount, Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]]]): Map[AwsAccount, Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]]] = {
    sgsMap.view.mapValues{
      case Right(secGroups) => Right(secGroups.filter(!_._1.isSuppressed))
      case left => left
    }.toMap
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
    val allSgs = discardSuppressedSgs(cacheService.getAllSgs)
    val allExposedKeys = cacheService.getAllExposedKeys
    val allPublicBuckets = cacheService.getAllPublicBuckets
    val allCredentials = cacheService.getAllCredentials

    val failures = collectFailures(List(allSgs, allExposedKeys, allPublicBuckets, allCredentials))

    if (failures.nonEmpty) {
      logger.warn(s"Skipping cloudwatch metrics update as some data is missing from the cache: $failures")
    } else {
      logger.info("Posting new metrics to cloudwatch")
      Cloudwatch.logAsMetric(allSgs, Cloudwatch.DataType.sgTotal)
      Cloudwatch.logAsMetric(allExposedKeys, Cloudwatch.DataType.iamKeysTotal)
      Cloudwatch.logAsMetric(allPublicBuckets, Cloudwatch.DataType.s3Total)
      Cloudwatch.logMetricsForCredentialsReport(allCredentials)
    }
  }

  if (environment.mode != Mode.Test) {
    val initialDelay =
      if (environment.mode == Mode.Prod) 15.minutes
      else Duration.Zero

    val cloudwatchSubscription = Observable.interval(initialDelay, 6.hours).subscribe { _ =>
      postCachedContentsAsMetrics()
    }

    lifecycle.addStopHook { () =>
      cloudwatchSubscription.unsubscribe()
      Future.successful(())
    }
  }
}
