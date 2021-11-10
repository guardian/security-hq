package logic

import logic.VulnerableIamUser.getCredsReportDisplayForAccount
import org.scalatest.{FreeSpec, Matchers}
import utils.attempt.{FailedAttempt, Failure}

class IamRemediationTest extends FreeSpec with Matchers {
   "getCredsReportDisplayForAccount" - {
    val failedAttempt: FailedAttempt = FailedAttempt(Failure("error", "error", 500))

    "if the either is a left, an empty list is output" in {
      val accountCredsLeft = Map(1 -> Left(failedAttempt))
      getCredsReportDisplayForAccount(accountCredsLeft) shouldEqual Nil
    }
    "if the either is a right, it is returned" in {
      val accountCredsRight = Map(1 -> Right(1))
      getCredsReportDisplayForAccount(accountCredsRight) shouldEqual List((1,1))
    }
    "given an empty map, return an empty list" in {
      getCredsReportDisplayForAccount(Map.empty) shouldEqual Nil
    }
    "if all eithers are a left, return an empty list" in {
      val accountCredsAllLeft = Map(1 -> Left(failedAttempt), 2 -> Left(failedAttempt), 3 -> Left(failedAttempt))
      getCredsReportDisplayForAccount(accountCredsAllLeft) shouldEqual Nil
    }
    "if every either is a right, output list contains same number of elements" in {
      val accountCredsAllRight = Map(1 -> Right(1), 2 -> Right(2), 3 -> Right(3))
      getCredsReportDisplayForAccount(accountCredsAllRight) should have length 3
    }
  }
}
