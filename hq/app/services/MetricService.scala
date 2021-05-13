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

  def dataMissingFrom[T](list: List[Map[AwsAccount, Either[FailedAttempt, T]]]): Boolean = {
    list.exists { dataMap =>
      dataMap.toSeq.exists(_._2.isLeft)
    }
  }

  def postCachedContentsAsMetrics(): Unit = {
    val allSgs = cacheService.getAllSgs
    val allExposedKeys = cacheService.getAllExposedKeys
    val allPublicBuckets = cacheService.getAllPublicBuckets
    val allCredentials = cacheService.getAllCredentials

    if(dataMissingFrom(List(allSgs, allExposedKeys, allPublicBuckets, allCredentials))) {
      logger.info("At least part of the data is missing - skipping cloudwatch update!")
      return
    }

    for {
      allGcpFindings <- cacheService.getGcpFindings
    } yield {
      Cloudwatch.logAsMetric(allSgs, Cloudwatch.DataType.sgTotal)
      Cloudwatch.logAsMetric(allExposedKeys, Cloudwatch.DataType.iamKeysTotal)
      Cloudwatch.logAsMetric(allPublicBuckets, Cloudwatch.DataType.s3Total)
      Cloudwatch.logMetricsForCredentialsReport(allCredentials)
      Cloudwatch.logMetricsForGCPFindings(allGcpFindings)
    }
  }

  if (environment.mode != Mode.Test) {
    val initialDelay =
      if (environment.mode == Mode.Prod) 5.minutes
      else Duration.Zero

    val cloudwatchSubscription = Observable.interval(initialDelay, 6.hours).subscribe { _ =>
      logger.info("Posting new metrics to cloudwatch")
      postCachedContentsAsMetrics()
    }

    lifecycle.addStopHook { () =>
      cloudwatchSubscription.unsubscribe()
      Future.successful(())
    }
  }
}
