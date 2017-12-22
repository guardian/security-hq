package service

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import model._
import org.joda.time.DateTime
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}
import play.api.Configuration
import utils.attempt.{Attempt, AttemptValues, FailedAttempt}

import scala.concurrent.ExecutionContext.Implicits.global

class CacheServiceTest extends FreeSpec with Eventually with Matchers with AttemptValues with BeforeAndAfterAll  {
  "update the data" - {
    val actorSystem = ActorSystem("test", ConfigFactory.empty())
    val config = Configuration.from(Map("hq.awsApiCacheInterval" -> 1, "hq.accounts" -> List.empty))
    def afterAll() {
      actorSystem.terminate()
    }

    "for Security Groups" in {
      val sgs1 = SGOpenPortsDetail("warning", "eu-west-1", "name-1", "id-1", "vpc-1", "tcp", "8080", "Yellow", false )
      val sgsPorts: Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]] = Right(List(sgs1 -> Set(Ec2Instance("id-1"))))
      val flaggedSgs = List( AwsAccount("security", "security", "security") -> sgsPorts )

      val updater = new CacheService(config)
      updater.update(actorSystem)( _ => Attempt.Right(flaggedSgs))(CacheService.updateSecurityGroups)

      eventually {
        CacheService.getFlaggedSecurityGroups().value() shouldBe flaggedSgs
      }
    }

    "for exposed keys" in {
      val flaggedResorces = ExposedIAMKeyDetail("key-1", "user", "F-1", "C1", "N", "GB", "", "")
      val exposedKeys: List[Either[FailedAttempt, (AwsAccount, TrustedAdvisorDetailsResult[ExposedIAMKeyDetail])]] =
        List( Right(AwsAccount("security", "security", "security") -> TrustedAdvisorDetailsResult("check-1", "warning", DateTime.now(), List(flaggedResorces), 1, 1, 1) ))

      val updater = new CacheService(config)
      updater.update(actorSystem)( _ => Attempt.Right(exposedKeys))(CacheService.updateExposedKeys)

      eventually {
        CacheService.getExposedKeys().value() shouldBe exposedKeys
      }
    }

    "for credential report" in {
      val report = CredentialReportDisplay(DateTime.now(), Seq(MachineUser("u1", NoKey, NoKey, Red, Some(1))))
      val credentialReport: Seq[(AwsAccount, Either[FailedAttempt, CredentialReportDisplay])] =
        Seq(AwsAccount("security", "security", "security") -> Right(report))

      val updater = new CacheService(config)
      updater.update(actorSystem)( _ => Attempt.Right(credentialReport))(CacheService.updateCredentialReport)

      eventually {
        CacheService.getCredentialReport().value() shouldBe credentialReport
      }
    }
  }
}
