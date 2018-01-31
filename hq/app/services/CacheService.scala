package services

import aws.ec2.EC2
import aws.iam.IAMClient
import aws.support.{TrustedAdvisor, TrustedAdvisorExposedIAMKeys}
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
  private val accounts = Config.getAwsAccounts(config)
  private val taAccounts = Config.getAwsAccounts(config).map(account => TrustedAdvisor.client(account)).zip(accounts)
  private val iamAccounts = Config.getAwsAccounts(config).map(account => IAMClient.client(account)).zip(accounts)

  private val startingCache = accounts.map(acc => (acc, Left(Failure.cacheServiceError(acc.id, "cache").attempt))).toMap
  private val credentialsBox: Box[Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]]] = Box(startingCache)
  private val exposedKeysBox: Box[Map[AwsAccount, Either[FailedAttempt, List[ExposedIAMKeyDetail]]]] = Box(startingCache)
  private val sgsBox: Box[Map[AwsAccount, Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]]]] = Box(startingCache)

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

  private def refreshCredentialsBoxTa(): Unit = {
    Logger.info("Started refresh of the Credentials data")
    for {
      allCredentialReports <- IAMClient.getAllCredentialReportsTa(iamAccounts)
    } yield {
      Logger.info("Sending the refreshed data to the Credentials Box")
      credentialsBox.send(allCredentialReports.toMap)
    }
  }

  private def refreshExposedKeysBoxTa(): Unit = {
    Logger.info("Started refresh of the Exposed Keys data")
    for {
      allExposedKeys <- TrustedAdvisorExposedIAMKeys.getAllExposedKeysTa(taAccounts)
    } yield {
      Logger.info("Sending the refreshed data to the Exposed Keys Box")
      exposedKeysBox.send(allExposedKeys.toMap)
    }
  }

  private def refreshSgsBoxTa(): Unit = {
    Logger.info("Started refresh of the Security Groups data")
    for {
      _ <- EC2.refreshSGSReportsTa(taAccounts)
      allFlaggedSgs <- EC2.allFlaggedSgsTa(taAccounts)
    } yield {
      Logger.info("Sending the refreshed data to the Security Groups Box")
      sgsBox.send(allFlaggedSgs.toMap)
    }
  }

  if (environment.mode != Mode.Test) {
    val credentialsSubscription = Observable.interval(500.millis, 1.minutes).subscribe { _ =>
      refreshCredentialsBoxTa()
    }

    val exposedKeysSubscription = Observable.interval(500.millis, 1.minutes).subscribe { _ =>
      refreshExposedKeysBoxTa()
    }

    val sgSubscription = Observable.interval(500.millis, 1.minutes).subscribe { _ =>
      refreshSgsBoxTa()
    }

    lifecycle.addStopHook { () =>
      credentialsSubscription.unsubscribe()
      exposedKeysSubscription.unsubscribe()
      sgSubscription.unsubscribe()
      Future.successful(())
    }
  }
}
