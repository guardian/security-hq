package logic

import config.Config
import logic.IamRemediation.{getCredsReportDisplayForAccount, identifyUsersWithOutdatedCredentials, lookupCredentialId, partitionOperationsByAllowedAccounts}
import model.iamremediation._
import model._
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}
import utils.attempt._

import scala.concurrent.ExecutionContext.Implicits.global

class IamRemediationTest extends FreeSpec with Matchers with AttemptValues {
  val date = new DateTime(2021, 1, 1, 1, 1)

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

  "identifyUsersWithOutdatedCredentials" - {
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
        val forbidden = partitionOperationsByAllowedAccounts(operations, allowedAccounts).operationsOnAccountsThatAreNotAllowed
        forbidden shouldEqual operations
      }
      "then allowed operations is empty" in {
        val allowed = partitionOperationsByAllowedAccounts(operations, allowedAccounts).allowedOperations
        allowed shouldEqual Nil
      }
    }

    "if there is one allowed account provided" - {
      val allowedAccounts = List("a")
      "then all operations that don't match provided allowed account are forbidden" in {
        val forbidden = partitionOperationsByAllowedAccounts(operations, allowedAccounts).operationsOnAccountsThatAreNotAllowed
        forbidden shouldEqual List(operationsForAccountB, operationsForAccountC)
      }
      "then allowed operations matches provided allowed account" in {
        val allowed = partitionOperationsByAllowedAccounts(operations, allowedAccounts).allowedOperations
        allowed shouldEqual List(operationsForAccountA)
      }
    }

    "if multiple allowed accounts are provided" - {
      val allowedAccounts = List("a", "b")
      "then all operations that don't match any allowed accounts are forbidden" in {
        val forbidden = partitionOperationsByAllowedAccounts(operations, allowedAccounts).operationsOnAccountsThatAreNotAllowed
        forbidden shouldEqual List(operationsForAccountC)
      }
      "then matching operations are allowed" in {
        val allowed = partitionOperationsByAllowedAccounts(operations, allowedAccounts).allowedOperations
        allowed shouldEqual List(operationsForAccountA, operationsForAccountB)
      }
    }

    "if operations to partition is empty" - {
      val allowedAccounts = List("a", "b")
      "then forbidden operations should also be empty" in {
        val forbidden = partitionOperationsByAllowedAccounts(Nil, allowedAccounts).operationsOnAccountsThatAreNotAllowed
        forbidden shouldEqual Nil
      }
      "then allowed operations should also be empty" in {
        val allowed = partitionOperationsByAllowedAccounts(Nil, allowedAccounts).allowedOperations
        allowed shouldEqual Nil
      }
    }
  }

  "lookupCredentialId" - {
    val nonMatchingAccessKey = CredentialMetadata("adam.fisher", "AKIAIOSFODNN1EXAMPLE", date.minusDays(1), CredentialActive)
    val matchingAccessKey = CredentialMetadata("amina.adewusi", "AKIAIOSFODNN2EXAMPLE", date, CredentialActive)
    val matchingAccessKey2 = CredentialMetadata("amina.adewusi", "AKIAIOSFODNN3EXAMPLE", date, CredentialActive)

    "given a key creation date matches a date in the metadata, return the correct metadata" in {
      val result = lookupCredentialId(date, List(matchingAccessKey, nonMatchingAccessKey))
      result.value.username shouldEqual matchingAccessKey.username
    }
    "given there are no matching key creation dates, return a failure" in {
      val result = lookupCredentialId(date, List(nonMatchingAccessKey))
      result.isFailedAttempt() shouldBe true
    }
    // both user's access keys sharing the exact same date is an edge case, because the creation date is accurate to the second
    // and it's unlikely both keys would be created at exactly the same time, but could happen, especially if created using the CLI.
    // If this happens then we won't know how to identify the keys' id, which is required to disable it, so we return a Failure.
    "given a key creation date matches two dates in the metadata, return a failure" in {
      val result = lookupCredentialId(date, List(matchingAccessKey2, matchingAccessKey))
      result.isFailedAttempt() shouldBe true 
    }
    // AWS's credential's report shows that a key's last rotated date is recorded to the second,
    // so this function must seek to match to the dates up to the second, but not the millisecond.
    "given a key creation date matches to the minute, but not the second, return a failure" in {
      val keyCreationDate = new DateTime(1,1,1,1,1,1)
      val metaDataCreationDate = new DateTime(1,1,1,1,1,2)
      val result = lookupCredentialId(keyCreationDate, List(matchingAccessKey.copy(creationDate = metaDataCreationDate)))
      result.isFailedAttempt() shouldBe true
    }
    "given a key creation date matches to the second, but not the millisecond, return the metadata" in {
      val keyCreationDate = new DateTime(1,1,1,1,1,1,1)
      val metaDataCreationDate = new DateTime(1,1,1,1,1,1,2)
      val result = lookupCredentialId(keyCreationDate, List(matchingAccessKey.copy(creationDate = metaDataCreationDate)))
      result.value.username shouldEqual matchingAccessKey.username
    }
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
