package service

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import aws.ec2.EC2
import config.Config
import model.AwsAccount
import play.api.Configuration
import rx.lang.scala.Observable
import utils.attempt.Attempt

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class SecurityGroupsUpdater(config: Configuration) {

  // highly parallel for making simultaneous requests across all AWS accounts
  val highlyAsyncExecutionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  private val accounts = Config.getAwsAccounts(config)

  def update(actorSystem: ActorSystem)(flaggedSgsFn: (List[AwsAccount]) => Attempt[SecurityGroups.FLAGGED_SGS] )(implicit ec: ExecutionContext) = {
    val cacheInterval = Config.getAwsApiCacheInterval(config)
    val subscription = Observable.interval(10.millisecond, cacheInterval).doOnEach { _ =>
      SecurityGroups.update(flaggedSgsFn(accounts))
    }.subscribe


    actorSystem.registerOnTermination(
      subscription.unsubscribe()
    )

 }
}
