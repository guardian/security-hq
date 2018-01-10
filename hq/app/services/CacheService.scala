package services

import aws.ec2.EC2
import aws.iam.IAMClient
import aws.support.TrustedAdvisorExposedIAMKeys
import com.gu.Box
import config.Config
import model._
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Environment, Logger, Mode}
import rx.lang.scala.Observable
import utils.attempt.{FailedAttempt, Failure}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._


class CacheService(config: Configuration, lifecycle: ApplicationLifecycle, environment: Environment)(implicit ec: ExecutionContext) {
  private val credentialsBox: Box[Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]]] = Box(Map.empty)
  private val exposedKeysBox: Box[Map[AwsAccount, Either[FailedAttempt, List[ExposedIAMKeyDetail]]]] = Box(Map.empty)
  private val sgsBox: Box[Map[AwsAccount, Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]]]] = Box(Map.empty)
  private val accounts = Config.getAwsAccounts(config)

  def getAllCredentials(): Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = credentialsBox.get()

  def getCredentialsForAccount(awsAccount: AwsAccount): Either[FailedAttempt, CredentialReportDisplay] = {
    credentialsBox.get().getOrElse(
      awsAccount,
      Left(Failure.cacheServiceError(awsAccount.id, "credentials").attempt)
    )
  }

  def getAllExposedKeys(): Map[AwsAccount, Either[FailedAttempt, List[ExposedIAMKeyDetail]]] = exposedKeysBox.get()

  def getExposedKeysForAccount(awsAccount: AwsAccount): Either[FailedAttempt, List[ExposedIAMKeyDetail]] = {
    exposedKeysBox.get().getOrElse(
      awsAccount,
      Left(Failure.cacheServiceError(awsAccount.id, "exposed keys").attempt)
    )
  }

  def getAllSgs(): Map[AwsAccount, Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]]] = sgsBox.get()

  def getSgsForAccount(awsAccount: AwsAccount): Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]] = {
    sgsBox.get().getOrElse(
      awsAccount,
      Left(Failure.cacheServiceError(awsAccount.id, "security group").attempt)
    )
  }

  private def refreshCredentialsBox(): Unit = {
    Logger.debug("Started refresh of the Credentials data")
    for {
      allCredentialReports <- IAMClient.getAllCredentialReports(accounts)
    } yield {
      Logger.debug("Sending the refreshed data to the Credentials Box")
      credentialsBox.send(allCredentialReports.toMap)
    }
  }

  private def refreshExposedKeysBox(): Unit = {
    Logger.debug("Started refresh of the Exposed Keys data")
    for {
      allExposedKeys <- TrustedAdvisorExposedIAMKeys.getAllExposedKeys(accounts)
    } yield {
      Logger.debug("Sending the refreshed data to the Exposed Keys Box")
      exposedKeysBox.send(allExposedKeys.toMap)
    }
  }

  private def refreshSgsBox(): Unit = {
    Logger.info("Started refresh of the Security Groups data")
    for {
      allFlaggedSgs <- EC2.allFlaggedSgs(accounts)
    } yield {
      Logger.info("Sending the refreshed data to the Security Groups Box")
      sgsBox.send(allFlaggedSgs.toMap)
    }
  }

  if (environment.mode != Mode.Test) {
    val credentialsSubscription = Observable.interval(500.millis, 5.minutes).subscribe { _ =>
      refreshCredentialsBox()
    }

    val exposedKeysSubscription = Observable.interval(500.millis, 5.minutes).subscribe { _ =>
      refreshExposedKeysBox()
    }

    val sgSubscription = Observable.interval(500.millis, 5.minutes).subscribe { _ =>
      refreshSgsBox()
    }

    lifecycle.addStopHook { () =>
      credentialsSubscription.unsubscribe()
      exposedKeysSubscription.unsubscribe()
      sgSubscription.unsubscribe()
      Future.successful(())
    }
  }
}
