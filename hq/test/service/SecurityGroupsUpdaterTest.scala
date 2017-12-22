package service

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import model.{AwsAccount, Ec2Instance, SGInUse, SGOpenPortsDetail}
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}
import play.api.Configuration
import utils.attempt.{Attempt, AttemptValues, FailedAttempt}

import scala.concurrent.ExecutionContext.Implicits.global

class SecurityGroupsUpdaterTest extends FreeSpec with Eventually with Matchers with AttemptValues with BeforeAndAfterAll  {
  "update the data" - {
    val actorSystem = ActorSystem("test", ConfigFactory.empty())

    def afterAll() {
      actorSystem.terminate()
    }

    "for Security Groups" in {
      val config = Configuration.from(Map("hq.awsApiCacheInterval" -> 1, "hq.accounts" -> List.empty))
      val sgs1 = SGOpenPortsDetail("warning", "eu-west-1", "name-1", "id-1", "vpc-1", "tcp", "8080", "Yellow", false )
      val sgsPorts: Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]] = Right(List(sgs1 -> Set(Ec2Instance("id-1"))))
      val flaggedSgs = List( AwsAccount("security", "security", "security") -> sgsPorts )

      val updater = new SecurityGroupsUpdater(config)
      updater.update(actorSystem)( _ => Attempt.Right(flaggedSgs))

      eventually {
        SecurityGroups.getFlaggedSecurityGroups().value() shouldBe flaggedSgs
      }

    }
  }
}
