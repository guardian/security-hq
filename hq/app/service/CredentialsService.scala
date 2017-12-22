package service

import akka.actor.ActorSystem
import com.gu.Box
import config.Config
import model.{AwsAccount, CredentialReportDisplay, ExposedIAMKeyDetail, TrustedAdvisorDetailsResult}
import play.api.Configuration
import rx.lang.scala.Observable
import service.CredentialsService.{CredentialReport, ExposedKeys}
import utils.attempt.{Attempt, FailedAttempt}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object CredentialsService {
  type ExposedKeys = List[Either[FailedAttempt, (AwsAccount, TrustedAdvisorDetailsResult[ExposedIAMKeyDetail])]]
  type CredentialReport = Seq[(AwsAccount, Either[FailedAttempt, CredentialReportDisplay])]

  private val exposedKeysBox = Box[ExposedKeys](List.empty)
  private val credetialReportBox = Box[CredentialReport](List.empty)

  def getExposedKeys()(implicit ec: ExecutionContext): Attempt[ExposedKeys] =
    Attempt.Right(exposedKeysBox.get())

  def updateExposedKeys(v: Attempt[ExposedKeys])(implicit ec: ExecutionContext) = {
    v.map(v => exposedKeysBox.send(v))
  }

  def getCredentialReport()(implicit ec: ExecutionContext): Attempt[CredentialReport] =
    Attempt.Right(credetialReportBox.get())

  def updateCredentialReport(v: Attempt[CredentialReport])(implicit ec: ExecutionContext) = {
    v.map(v => credetialReportBox.send(v))
  }
}

class CredentialServiceUpdater(config: Configuration) {

  private val accounts = Config.getAwsAccounts(config)

  def updateExposedKeys(actorSystem: ActorSystem)(exposedIAMKeysFn: (List[AwsAccount]) => Attempt[ExposedKeys] )(implicit ec: ExecutionContext) = {
    val cacheInterval = Config.getAwsApiCacheInterval(config)
    val subscription = Observable.interval(10.millisecond, cacheInterval).doOnEach { _ =>

      CredentialsService.updateExposedKeys(exposedIAMKeysFn(accounts))
    }.subscribe

    actorSystem.registerOnTermination(
      subscription.unsubscribe()
    )
  }

  def updateCredentialReport(actorSystem: ActorSystem)(exposedIAMKeysFn: (List[AwsAccount]) => Attempt[CredentialReport] )(implicit ec: ExecutionContext) = {
    val cacheInterval = Config.getAwsApiCacheInterval(config)
    val subscription = Observable.interval(10.millisecond, cacheInterval).doOnEach { _ =>

      CredentialsService.updateCredentialReport(exposedIAMKeysFn(accounts))
    }.subscribe

    actorSystem.registerOnTermination(
      subscription.unsubscribe()
    )
  }


}
