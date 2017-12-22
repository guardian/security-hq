package service

import akka.actor.ActorSystem
import com.gu.Box
import config.Config
import model.{AwsAccount, SGInUse, SGOpenPortsDetail}
import play.api.Configuration
import rx.lang.scala.Observable
import utils.attempt.{Attempt, FailedAttempt}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


object SecurityGroupsService {

  type EitherFlaggedSgs = Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]]
  type FlaggedSgs = List[(AwsAccount, EitherFlaggedSgs)]

  private val sgsBox = Box[FlaggedSgs](List.empty)

  def getFlaggedSecurityGroups()(implicit ec: ExecutionContext): Attempt[FlaggedSgs] = Attempt.Right(sgsBox.get())

  def update(v: Attempt[FlaggedSgs])(implicit ec: ExecutionContext) = {
    v.map ( v => sgsBox.send(v))
  }

}

class SecurityGroupsUpdater(config: Configuration) {
  private val accounts = Config.getAwsAccounts(config)

  def update(actorSystem: ActorSystem)(flaggedSgsFn: (List[AwsAccount]) => Attempt[SecurityGroupsService.FlaggedSgs] )(implicit ec: ExecutionContext) = {
    val cacheInterval = Config.getAwsApiCacheInterval(config)
    val subscription = Observable.interval(10.millisecond, cacheInterval).doOnEach { _ =>
      SecurityGroupsService.update(flaggedSgsFn(accounts))
    }.subscribe

    actorSystem.registerOnTermination(
      subscription.unsubscribe()
    )
  }
}
