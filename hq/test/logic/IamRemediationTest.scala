package logic

import config.Config
import logic.IamRemediation.{getCredsReportDisplayForAccount, identifyAllUsersWithOutdatedCredentials, identifyUsersWithOutdatedCredentials}
import model.{AccessKey, AccessKeyDisabled, AccessKeyEnabled, AwsAccount, CredentialReportDisplay, Green, HumanUser, MachineUser, NoKey}
import model.{IamUserRemediationHistory, OutdatedCredential, RemediationOperation, Warning}
import org.joda.time.DateTime
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
      getCredsReportDisplayForAccount(accountCredsRight) shouldEqual List((1, 1))
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

  "identifyUsersWithOutdatedCredentials" - {
    val date = new DateTime(2021, 1, 1, 1, 1)
    val humanAccessKeyOldAndEnabled = AccessKey(AccessKeyEnabled, Some(date.minusMonths(4)))
    val humanAccessKeyOldAndDisabled = AccessKey(AccessKeyDisabled, Some(date.minusMonths(4)))
    val machineAccessKeyOldAndEnabled = AccessKey(AccessKeyEnabled, Some(date.minusMonths(13)))
    val machineAccessKeyOldAndEnabledOnTimeThreshold = AccessKey(AccessKeyEnabled, Some(date.minusDays(Config.iamMachineUserRotationCadence.toInt)))
    val machineAccessKeyOldAndDisabled = AccessKey(AccessKeyDisabled, Some(date.minusMonths(13)))
    val humanEnabledAccessKeyHealthy = AccessKey(AccessKeyEnabled, Some(date.minusMonths(1)))
    val machineEnabledAccessKeyHealthy = AccessKey(AccessKeyEnabled, Some(date.minusMonths(11)))
    val noAccessKey = AccessKey(NoKey, None)
    val account = AwsAccount("testAccountId", "testAccount", "roleArn", "12345")
    val humanWithOneOldEnabledAccessKey = HumanUser("amina.adewusi", true, humanAccessKeyOldAndEnabled, noAccessKey, Green, None, None, Nil)
    val humanWithOneOldDisabledAccessKey = HumanUser("adam.fisher", true, noAccessKey, humanAccessKeyOldAndDisabled, Green, None, None, Nil)
    val humanWithNoKeys = HumanUser("jorge.azevedo", true, noAccessKey, noAccessKey, Green, None, None, Nil)
    val humanWithHealthyKey = HumanUser("jon.soul", true, noAccessKey, humanEnabledAccessKeyHealthy, Green, None, None, Nil)
    val machineWithOneOldEnabledAccessKey = MachineUser("machine1", machineAccessKeyOldAndEnabled, noAccessKey, Green, None, None, Nil)
    val machineWithOneOldEnabledAccessKey2 = MachineUser("machine2", machineAccessKeyOldAndEnabledOnTimeThreshold, noAccessKey, Green, None, None, Nil)
    val machineWithOneOldDisabledAccessKey = MachineUser("machine3", noAccessKey, machineAccessKeyOldAndDisabled, Green, None, None, Nil)
    val machineWithHealthyKey = MachineUser("machine4", noAccessKey, machineEnabledAccessKeyHealthy, Green, None, None, Nil)

    "given a vulnerable human user, return that user" in {
      val credsReport = CredentialReportDisplay(date, Seq(), Seq(humanWithOneOldEnabledAccessKey))
      identifyUsersWithOutdatedCredentials(account, credsReport, date).map(_.username) shouldEqual List("amina.adewusi")
    }
    "given a vulnerable machine user, return that user" in {
      val credsReport = CredentialReportDisplay(date, Seq(machineWithOneOldEnabledAccessKey), Seq())
      identifyUsersWithOutdatedCredentials(account, credsReport, date).map(_.username) shouldEqual List("machine1")
    }
    "given a vulnerable human and machine user, return both users" in {
      val credsReport = CredentialReportDisplay(date, Seq(machineWithOneOldEnabledAccessKey), Seq(humanWithOneOldEnabledAccessKey))
      identifyUsersWithOutdatedCredentials(account, credsReport, date).map(_.username) should contain allOf ("amina.adewusi", "machine1")
    }
    "given users with old disabled keys, return an empty list" in {
      val credsReport = CredentialReportDisplay(date, Seq(machineWithOneOldDisabledAccessKey), Seq(humanWithOneOldDisabledAccessKey))
      identifyUsersWithOutdatedCredentials(account, credsReport, date) shouldBe empty
    }
    "given no users with access keys, return an empty list" in {
      val credsReport = CredentialReportDisplay(date, Seq(), Seq(humanWithNoKeys))
      identifyUsersWithOutdatedCredentials(account, credsReport, date) shouldBe empty
    }
    "given no vulnerable access keys, return an empty list" in {
      val credsReport = CredentialReportDisplay(date, Seq(machineWithHealthyKey), Seq(humanWithHealthyKey))
      identifyUsersWithOutdatedCredentials(account, credsReport, date) shouldBe empty
    }
    "given a machine user whose access key was last rotated exactly on the last acceptable healthy rotation date, return user" in {
      val credsReport = CredentialReportDisplay(date, Seq(machineWithOneOldEnabledAccessKey2), Seq())
      identifyUsersWithOutdatedCredentials(account, credsReport, date) should have length 1
    }
  }

  "calculateOutstandingOperations" - {
    "TODO" ignore {}
  }

  "partitionOperationsByAllowedAccounts" - {
    val operationsForAccountA = operationForAccountId("a", "machineUser1")
    val operationsForAccountB = operationForAccountId("b", "machineUser2")
    val operationsForAccountC = operationForAccountId("c", "machineUser3")
    val operations = List(operationsForAccountA, operationsForAccountB, operationsForAccountC)

    "if allowedAccounts is empty" - {
      val allowedAccounts = Nil
      "then all operations are not allowed" in {
        val result = partitionOperationsByAllowedAccounts(operations, allowedAccounts).operationsOnAccountsThatAreNotAllowed
        result shouldEqual operations
      }
      "then allowed operations is empty" in {
        val result = partitionOperationsByAllowedAccounts(operations, allowedAccounts).allowedOperations
        result shouldEqual Nil
      }

      "if there is one allowed account provided" - {
        val allowedAccounts = List("a")
        "allowed operations matches provided allowed account" in {
          val result = partitionOperationsByAllowedAccounts(operations, allowedAccounts).allowedOperations
          result shouldEqual List(operationsForAccountA)
        }
        "operationsOnAccountsThatAreNotAllowed contains all operations that do not match provided allowed account" in {
          val result = partitionOperationsByAllowedAccounts(operations, allowedAccounts).operationsOnAccountsThatAreNotAllowed
          result shouldEqual List(operationsForAccountB, operationsForAccountC)
        }
      }

      "if multiple allowed accounts are provided" - {
        val allowedAccounts = List("a", "b")
        "matching operations are allowed" in {
          val result = partitionOperationsByAllowedAccounts(operations, allowedAccounts).allowedOperations
          result shouldEqual List(operationsForAccountA, operationsForAccountB)
        }
        "operations that don't match any account are forbidden" in {
          val result = partitionOperationsByAllowedAccounts(operations, allowedAccounts).operationsOnAccountsThatAreNotAllowed
          result shouldEqual List(operationsForAccountC)
        }
      }

    }
  }

  "lookupCredentialId" - {
    "TODO" ignore {}
  }

  "formatRemediationOperation" - {
    "TODO" ignore {}
  }

  def operationForAccountId(id: String, username: String): RemediationOperation = {
    val machineUser = MachineUser(username, AccessKey(NoKey, None), AccessKey(NoKey, None), Green, None, None, Nil)
    RemediationOperation(IamUserRemediationHistory(AwsAccount(id, "", "", ""),
      machineUser
      , Nil), Warning, OutdatedCredential, new DateTime())
  }
}
