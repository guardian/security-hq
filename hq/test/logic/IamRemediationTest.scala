package logic

import config.Config
import config.Config.{daysBetweenFinalNotificationAndRemediation, daysBetweenWarningAndFinalNotification}
import logic.IamRemediation._
import model.iamremediation._
import model._
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}
import utils.attempt.{FailedAttempt, Failure}


class IamRemediationTest extends FreeSpec with Matchers {
  val date = new DateTime(2021, 1, 1, 1, 1)
  val humanAccessKeyOldAndEnabled1 = AccessKey(AccessKeyEnabled, Some(date.minusMonths(4)))
  val humanAccessKeyOldAndEnabled2 = AccessKey(AccessKeyEnabled, Some(date.minusMonths(4)))
  val machineAccessKeyOldAndEnabled = AccessKey(AccessKeyEnabled, Some(date.minusMonths(13)))
  val machineAccessKeyOldAndEnabledOnTimeThreshold = AccessKey(AccessKeyEnabled, Some(date.minusDays(Config.iamMachineUserRotationCadence.toInt)))
  val humanAccessKeyHealthAndEnabled = AccessKey(AccessKeyEnabled, Some(date.minusMonths(1)))
  val noAccessKey = AccessKey(NoKey, None)
  val account = AwsAccount("testAccountId", "testAccount", "roleArn", "12345")
  val humanWithOneOldEnabledAccessKey = HumanUser("amina.adewusi", true, humanAccessKeyOldAndEnabled1, noAccessKey, Green, None, None, Nil)
  val humanWithTwoOldEnabledAccessKeys = HumanUser("nic.long", true, humanAccessKeyOldAndEnabled1, humanAccessKeyOldAndEnabled2, Green, None, None, Nil)
  val humanWithHealthyKey = HumanUser("jon.soul", true, noAccessKey, humanAccessKeyHealthAndEnabled, Green, None, None, Nil)
  val machineWithOneOldEnabledAccessKey = MachineUser("machine1", machineAccessKeyOldAndEnabled, noAccessKey, Green, None, None, Nil)
  val machineWithOneOldEnabledAccessKey2 = MachineUser("machine2", machineAccessKeyOldAndEnabledOnTimeThreshold, noAccessKey, Green, None, None, Nil)

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
      val humanWithOneOldDisabledAccessKey = HumanUser("adam.fisher", true, noAccessKey, AccessKey(AccessKeyDisabled, Some(date.minusMonths(4))), Green, None, None, Nil)
      val machineAccessKeyOldAndDisabled = AccessKey(AccessKeyDisabled, Some(date.minusMonths(13)))
      val machineWithOneOldDisabledAccessKey = MachineUser("machine3", noAccessKey, machineAccessKeyOldAndDisabled, Green, None, None, Nil)
      val credsReport = CredentialReportDisplay(date, Seq(machineWithOneOldDisabledAccessKey), Seq(humanWithOneOldDisabledAccessKey))
      identifyUsersWithOutdatedCredentials(account, credsReport, date) shouldBe empty
    }
    "given no users with access keys, return an empty list" in {
      val humanWithNoKeys = HumanUser("jorge.azevedo", true, noAccessKey, noAccessKey, Green, None, None, Nil)
      val credsReport = CredentialReportDisplay(date, Seq(), Seq(humanWithNoKeys))
      identifyUsersWithOutdatedCredentials(account, credsReport, date) shouldBe empty
    }
    "given no vulnerable access keys, return an empty list" in {
      val machineAccessKeyHealthyAndEnabled = AccessKey(AccessKeyEnabled, Some(date.minusMonths(11)))
      val machineWithHealthyKey = MachineUser("machine4", noAccessKey, machineAccessKeyHealthyAndEnabled, Green, None, None, Nil)
      val credsReport = CredentialReportDisplay(date, Seq(machineWithHealthyKey), Seq(humanWithHealthyKey))
      identifyUsersWithOutdatedCredentials(account, credsReport, date) shouldBe empty
    }
    "given a machine user whose access key was last rotated exactly on the last acceptable healthy rotation date, return user" in {
      val credsReport = CredentialReportDisplay(date, Seq(machineWithOneOldEnabledAccessKey2), Seq())
      identifyUsersWithOutdatedCredentials(account, credsReport, date) should have length 1
    }
  }

  "calculateOutstandingOperations" - {
    // human activities - warning
    val humanActivityWarningLastNotificationGreaterThanCadence = IamRemediationActivity(account.id, humanWithOneOldEnabledAccessKey.username, date.minusDays(daysBetweenWarningAndFinalNotification + 1), Warning, OutdatedCredential, humanAccessKeyOldAndEnabled1.lastRotated.get)
    val humanActivityWarningLastNotificationEqualToCadence = IamRemediationActivity(account.id, humanWithTwoOldEnabledAccessKeys.username, date.minusDays(daysBetweenWarningAndFinalNotification), Warning, OutdatedCredential, humanAccessKeyOldAndEnabled1.lastRotated.get)
    // human activities - final
    val humanActivityFinalLastNotificationGreaterThanCadence = IamRemediationActivity(account.id, humanWithTwoOldEnabledAccessKeys.username, date.minusDays(daysBetweenFinalNotificationAndRemediation + 1), FinalWarning, OutdatedCredential, humanAccessKeyOldAndEnabled2.lastRotated.get)
    val humanActivityFinalLastNotificationEqualToCadence = IamRemediationActivity(account.id, humanWithOneOldEnabledAccessKey.username, date.minusDays(daysBetweenFinalNotificationAndRemediation), FinalWarning, OutdatedCredential, humanAccessKeyOldAndEnabled1.lastRotated.get)
    // human activities - remediation
    val humanActivityRemediationUnhealthy = IamRemediationActivity(account.id, humanWithOneOldEnabledAccessKey.username, date, Remediation, OutdatedCredential, humanAccessKeyOldAndEnabled1.lastRotated.get)
    // human users
    val humanBothKeysRequireAction = IamUserRemediationHistory(account, humanWithTwoOldEnabledAccessKeys, List(humanActivityFinalLastNotificationGreaterThanCadence, humanActivityWarningLastNotificationEqualToCadence))
    val humanOneKeyRequiresAction = IamUserRemediationHistory(account, humanWithOneOldEnabledAccessKey, List(humanActivityWarningLastNotificationGreaterThanCadence))
    val humanOneKeyFinalWarning = IamUserRemediationHistory(account, humanWithOneOldEnabledAccessKey, List(humanActivityWarningLastNotificationEqualToCadence, humanActivityFinalLastNotificationEqualToCadence))
    val humanOneKeyRemediation = IamUserRemediationHistory(account, humanWithOneOldEnabledAccessKey, List(humanActivityWarningLastNotificationEqualToCadence, humanActivityFinalLastNotificationEqualToCadence, humanActivityRemediationUnhealthy))
    // machine activities - warning
    val machineActivityWarningLastNotificationEqualToCadence = IamRemediationActivity(account.id, machineWithOneOldEnabledAccessKey.username, date.minusDays(daysBetweenWarningAndFinalNotification), Warning, OutdatedCredential, machineAccessKeyOldAndEnabled.lastRotated.get)
    val machineActivityWarningKeyLastRotatedEqualCadenceThreshold = IamRemediationActivity(account.id, machineWithOneOldEnabledAccessKey.username, date.minusWeeks(3), Warning, OutdatedCredential, machineAccessKeyOldAndEnabledOnTimeThreshold.lastRotated.get)
    // machine access keys
    val machineWithTwoOldEnabledAccessKeys = MachineUser("machine5", machineAccessKeyOldAndEnabledOnTimeThreshold, machineAccessKeyOldAndEnabled, Green, None, None, Nil)
    // machine users
    val machineOneKeyWarning = IamUserRemediationHistory(account, machineWithOneOldEnabledAccessKey, List(machineActivityWarningLastNotificationEqualToCadence))
    val machineTwoKeysRequireAction = IamUserRemediationHistory(account, machineWithTwoOldEnabledAccessKeys, List(machineActivityWarningLastNotificationEqualToCadence, machineActivityWarningKeyLastRotatedEqualCadenceThreshold))


    "given two users, each with 2 keys that require operations, output a list of size 4" in {
      calculateOutstandingOperations(List(humanBothKeysRequireAction, machineTwoKeysRequireAction), date) should have length 4
    }
    "given two users, each with 1 key that requires an operation, output a list of size 2" in {
      calculateOutstandingOperations(List(humanOneKeyRequiresAction,  machineOneKeyWarning), date) should have length 2
    }
    "given one user with 2 keys that require an operation, output a list of size 2" in {
      calculateOutstandingOperations(List(machineTwoKeysRequireAction), date) should have length 2
    }
    "given one user with 1 key that requires an operation, output a list of size 1" in {
      calculateOutstandingOperations(List(humanOneKeyRequiresAction), date) should have length 1
    }
    // this scenario should never happen, keeping this test here to note this.
    "given an empty input list, return an empty output list" in {
      calculateOutstandingOperations(Nil, date) shouldEqual Nil
    }

    "identifyRemediationOperation" - {
      "given IamRemediationActivity is a None, return Warning operation" in {
        val result = identifyRemediationOperation(mostRecentRemediationActivity = None, now = date, humanBothKeysRequireAction).map(_.iamRemediationActivityType)
        result shouldBe Some(Warning)
      }
      "if the most recent activity is a Warning with a date more than `Config.daysBetweenWarningAndFinalNotification` days ago, return a Final operation" in {
        val input = activity(daysBetweenWarningAndFinalNotification + 1, Warning, humanAccessKeyOldAndEnabled1)
        val result = identifyRemediationOperation(Some(input), date, humanOneKeyRequiresAction).map(_.iamRemediationActivityType)
        result shouldBe Some(FinalWarning)
      }
      "if the most recent activity is a Warning with a date exactly `Config.daysBetweenWarningAndFinalNotification` days ago, return a Final operation" in {
        val input = activity(daysBetweenWarningAndFinalNotification, Warning, machineAccessKeyOldAndEnabled)
        val result = identifyRemediationOperation(Some(input), date, machineOneKeyWarning).map(_.iamRemediationActivityType)
        result shouldBe Some(FinalWarning)
      }
      "if the most recent activity is a Warning with a date less than `Config.daysBetweenWarningAndFinalNotification` days ago, return a None" in {
        val machineActivityWarningHealthy = IamRemediationActivity(account.id, machineWithOneOldEnabledAccessKey.username, date.minusDays(daysBetweenWarningAndFinalNotification - 1), Warning, OutdatedCredential, machineAccessKeyOldAndEnabled.lastRotated.get)
        val machineOneWarningKeyDoesNotRequireAction = IamUserRemediationHistory(account, machineWithOneOldEnabledAccessKey, List(machineActivityWarningHealthy))
        val input = activity(daysBetweenWarningAndFinalNotification - 1, Warning, machineAccessKeyOldAndEnabled)
        val result = identifyRemediationOperation(Some(input), date, machineOneWarningKeyDoesNotRequireAction).map(_.iamRemediationActivityType)
        result shouldBe empty
      }
      "if the most recent activity is a Final with a date more than `Config.daysBetweenFinalNotificationAndRemediation` days ago, return a Remediation operation" in {
        val input = activity(daysBetweenFinalNotificationAndRemediation + 1, FinalWarning, humanAccessKeyOldAndEnabled1)
        val result = identifyRemediationOperation(Some(input), date, humanBothKeysRequireAction).map(_.iamRemediationActivityType)
        result shouldBe Some(Remediation)
      }
      "if the most recent activity is a Final with a date exactly `Config.daysBetweenFinalNotificationAndRemediation` days ago, return a Remediation operation" in {
        val input = activity(daysBetweenFinalNotificationAndRemediation, FinalWarning, humanAccessKeyOldAndEnabled1)
        val result = identifyRemediationOperation(Some(input), date, humanOneKeyFinalWarning).map(_.iamRemediationActivityType)
        result shouldBe Some(Remediation)
      }
      "if the most recent activity is a Final with a date less than `Config.daysBetweenFinalNotificationAndRemediation` days ago, return a None" in {
        val machineActivityFinalNotificationLessThanCadence = IamRemediationActivity(account.id, machineWithOneOldEnabledAccessKey.username, date.minusDays(daysBetweenFinalNotificationAndRemediation - 1), FinalWarning, OutdatedCredential, machineAccessKeyOldAndEnabled.lastRotated.get)
        val machineOneFinalKeyDoesNotRequireAction = IamUserRemediationHistory(account, machineWithOneOldEnabledAccessKey, List(machineActivityFinalNotificationLessThanCadence))
        val input = activity(daysBetweenFinalNotificationAndRemediation - 1, FinalWarning, machineAccessKeyOldAndEnabled)
        val result = identifyRemediationOperation(Some(input), date, machineOneFinalKeyDoesNotRequireAction).map(_.iamRemediationActivityType)
        result shouldBe empty
      }
      // The most recent activity being a Remediation is an edge case, because it means that the access key has not been successfully disabled.
      // In this edge case, set the operation to Remediation so that Security HQ can try to disable the key again.
      "if the most recent activity is a Remediation, return a Remediation" in {
        val input = activity(1, Remediation, humanAccessKeyOldAndEnabled1)
        val result = identifyRemediationOperation(Some(input), date, humanOneKeyRemediation).map(_.iamRemediationActivityType)
        result shouldBe Some(Remediation)
      }
    }
    "identifyMostRecentIamRemediationActivity" - {
      "if the key's most recent activity is Warning, return Warning" in {
        val key = machineAccessKeyOldAndEnabled
        val recentActivity = activity(daysBetweenWarningAndFinalNotification, Warning, key)
        val history = IamUserRemediationHistory(account, machineWithOneOldEnabledAccessKey, List(recentActivity))
        val result = identifyMostRecentActivity(history, key)
        result.map(_.iamRemediationActivityType) shouldBe Some(Warning)
      }
      "if the key's most recent activity is Final, return Final" in {
        val key = humanAccessKeyOldAndEnabled1
        val recentActivity = activity(daysBetweenFinalNotificationAndRemediation, FinalWarning, key)
        val history = IamUserRemediationHistory(account, humanWithOneOldEnabledAccessKey, List(recentActivity))
        val result = identifyMostRecentActivity(history, key)
        result.map(_.iamRemediationActivityType) shouldBe Some(FinalWarning)
      }
      "if the key's most recent activity is Remediation, return Remediation" in {
        val key = humanAccessKeyOldAndEnabled1
        val recentActivity = activity(1, Remediation, key)
        val history = IamUserRemediationHistory(account, humanWithOneOldEnabledAccessKey, List(recentActivity))
        val result = identifyMostRecentActivity(history, key)
        result.map(_.iamRemediationActivityType) shouldBe Some(Remediation)
      }
      "given a key does not have any activity, return None" in {
        val recentActivity = Nil
        val machineNoActivity = IamUserRemediationHistory(account, machineWithOneOldEnabledAccessKey2, recentActivity)
        identifyMostRecentActivity(machineNoActivity, machineAccessKeyOldAndEnabledOnTimeThreshold) shouldBe empty
      }
    }
    "identifyVulnerableKeys" - {
      "given a human user with 1 vulnerable access key, return that key" in {
        identifyVulnerableKeys(humanOneKeyRequiresAction, date).map(_.lastRotated) shouldEqual List(humanAccessKeyOldAndEnabled1.lastRotated)
      }
      "given a machine user with 1 vulnerable access key, return that key" in {
        identifyVulnerableKeys(machineOneKeyWarning, date).map(_.lastRotated) shouldEqual List(machineAccessKeyOldAndEnabled.lastRotated)
      }
      "given 2 vulnerable access keys, return both keys" in {
        identifyVulnerableKeys(humanBothKeysRequireAction, date).map(_.lastRotated) shouldEqual List(humanAccessKeyOldAndEnabled1.lastRotated, humanAccessKeyOldAndEnabled2.lastRotated)
      }
      // this scenario should never happen, becuase this function should only be called if there is at least one problem access key.
      "given no vulnerable access keys, return an empty list" in {
        val humanActivityRemediationHealthy = IamRemediationActivity(account.id, humanWithHealthyKey.username, date.minusMonths(2), Remediation, OutdatedCredential, humanAccessKeyHealthAndEnabled.lastRotated.get)
        val humanHealthy = IamUserRemediationHistory(account, humanWithHealthyKey, List(humanActivityRemediationHealthy))
        identifyVulnerableKeys(humanHealthy, date) shouldBe empty
      }
    }
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
  def activity(dayOffset: Int, activityType: IamRemediationActivityType, accessKey: AccessKey) =
    IamRemediationActivity(account.id, "username", date.minusDays(dayOffset), activityType, OutdatedCredential, accessKey.lastRotated.get)
}
