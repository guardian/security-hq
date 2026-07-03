package metrics

import model.AwsAccount
import utils.attempt.{FailedAttempt, Failure}

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class MetricsCollectorTest extends AnyFreeSpec with Matchers {

  private val accountA = AwsAccount("a", "Account A", "arn:a", "111")
  private val accountB = AwsAccount("b", "Account B", "arn:b", "222")

  private def failure(account: AwsAccount): FailedAttempt =
    Failure.cacheServiceErrorPerAccount(account.id, "test").attempt

  "collectFailures" - {
    "returns nothing when every account succeeded" in {
      val data = Map[AwsAccount, Either[FailedAttempt, List[Int]]](
        accountA -> Right(List(1, 2)),
        accountB -> Right(Nil)
      )
      MetricsCollector.collectFailures(List(data)) shouldBe empty
    }

    "collects a failure for each Left across all data sets" in {
      val keys = Map[AwsAccount, Either[FailedAttempt, List[Int]]](
        accountA -> Left(failure(accountA)),
        accountB -> Right(Nil)
      )
      val buckets = Map[AwsAccount, Either[FailedAttempt, List[Int]]](
        accountB -> Left(failure(accountB))
      )
      val result = MetricsCollector.collectFailures(List(keys, buckets))
      result.map(_._1).toSet shouldEqual Set(accountA, accountB)
    }
  }
}
