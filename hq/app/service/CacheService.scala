package service

import akka.actor.ActorSystem
import com.gu.Box
import config.Config
import model._
import play.api.Configuration
import rx.lang.scala.Observable
import utils.attempt.{Attempt, FailedAttempt}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class CacheService(config: Configuration) {
  private val accounts = Config.getAwsAccounts(config)

  def update[T](actorSystem: ActorSystem)(awsWorkerFn: (List[AwsAccount]) => Attempt[T])(boxUpdaterFn: Attempt[T] => Attempt[Unit])(implicit ec: ExecutionContext) = {
    val cacheInterval = Config.getAwsApiCacheInterval(config)
    val subscription = Observable.interval(10.millisecond, cacheInterval).doOnEach { _ =>
      boxUpdaterFn(awsWorkerFn(accounts))
    }.subscribe

    actorSystem.registerOnTermination(
      subscription.unsubscribe()
    )
  }
}

object CacheService {
  type EitherFlaggedSgs = Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]]
  type FlaggedSgs = List[(AwsAccount, EitherFlaggedSgs)]
  type ExposedKeys = List[Either[FailedAttempt, (AwsAccount, TrustedAdvisorDetailsResult[ExposedIAMKeyDetail])]]
  type CredentialReport = Seq[(AwsAccount, Either[FailedAttempt, CredentialReportDisplay])]

  private val exposedKeysBox = Box[ExposedKeys](List.empty)
  private val credentialReportBox = Box[CredentialReport](List.empty)
  private val sgsBox = Box[FlaggedSgs](List.empty)

  def getExposedKeys()(implicit ec: ExecutionContext): Attempt[ExposedKeys] =
    Attempt.Right(exposedKeysBox.get())

  def updateExposedKeys(v: Attempt[ExposedKeys])(implicit ec: ExecutionContext) = {
    v.map(v => exposedKeysBox.send(v))
  }

  def getCredentialReport()(implicit ec: ExecutionContext): Attempt[CredentialReport] =
    Attempt.Right(credentialReportBox.get())

  def updateCredentialReport(v: Attempt[CredentialReport])(implicit ec: ExecutionContext) = {
    v.map(v => credentialReportBox.send(v))
  }

  def getFlaggedSecurityGroups()(implicit ec: ExecutionContext): Attempt[FlaggedSgs] = Attempt.Right(sgsBox.get())

  def updateSecurityGroups(v: Attempt[FlaggedSgs])(implicit ec: ExecutionContext) = {
    v.map(v => sgsBox.send(v))
  }
}
