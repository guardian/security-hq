package services

import aws.ec2.EC2
import com.gu.Box
import config.Config
import model.{AwsAccount, SGInUse, SGOpenPortsDetail}
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Environment, Mode}
import rx.lang.scala.Observable
import utils.attempt.{FailedAttempt, Failure}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._


class CacheService(config: Configuration, lifecycle: ApplicationLifecycle, environment: Environment)(implicit ec: ExecutionContext) {
  private val sgsBox: Box[Map[AwsAccount, Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]]]] = Box(Map.empty)
  private val accounts = Config.getAwsAccounts(config)

  def getAllSgs(): Map[AwsAccount, Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]]] = sgsBox.get()

  def getSgsForAccount(awsAccount: AwsAccount): Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]] = {
    sgsBox.get().getOrElse(
      awsAccount,
      Left(Failure("unable to find account data in the cache", "No security group data available", 500, Some(awsAccount.id)).attempt)
    )
  }

  private def refreshSgsBox(): Unit = {
    println("Started refresh of the Security Groups")
    for {
      allFlaggedSgs <- EC2.allFlaggedSgs(accounts)
    } yield {
      println("Sending the refreshed data to the Security Groups Box")
      sgsBox.send(allFlaggedSgs.toMap)
    }
  }

  if (environment.mode != Mode.Test) {
    val sgSubscription = Observable.interval(500.millis, 5.minutes).subscribe { _ =>
      refreshSgsBox()
    }

    lifecycle.addStopHook { () =>
      sgSubscription.unsubscribe()
      Future.successful(())
    }
  }
}
