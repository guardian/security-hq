package logic

import aws.AwsClient
import config.CoreConfig
import config.CoreConfig.{daysBetweenFinalNotificationAndRemediation, daysBetweenWarningAndFinalNotification}
import db.IamRemediationDb
import logic.IamOutdatedCredentials.*
import model.*
import org.joda.time.DateTime
import org.scalatest.Inside.inside
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.iam.IamAsyncClient
import software.amazon.awssdk.services.iam.model.{
  AccessKeyMetadata,
  ListAccessKeysRequest,
  ListAccessKeysResponse,
  StatusType,
  UpdateAccessKeyResponse
}
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.{PublishRequest, PublishResponse}
import utils.attempt.{Attempt, AttemptValues}

import java.time.Instant
import java.util.concurrent.CompletableFuture
import scala.concurrent.ExecutionContext.Implicits.global

class IamOutdatedCredentialsTest extends AnyFreeSpec with Matchers with OptionValues with AttemptValues {
  val date = new DateTime(2021, 1, 1, 1, 1)
  val humanAccessKeyOldAndEnabled1 = AccessKey(AccessKeyEnabled, Some(date.minusMonths(4)))
  val humanAccessKeyOldAndEnabled2 = AccessKey(AccessKeyEnabled, Some(date.minusMonths(4)))
  val machineAccessKeyOldAndEnabled = AccessKey(AccessKeyEnabled, Some(date.minusMonths(13)))
  val machineAccessKeyOldAndEnabledOnTimeThreshold =
    AccessKey(AccessKeyEnabled, Some(date.minusDays(CoreConfig.iamMachineUserRotationCadence.toInt)))
  val humanAccessKeyHealthAndEnabled = AccessKey(AccessKeyEnabled, Some(date.minusMonths(1)))
  val noAccessKey = AccessKey(NoKey, None)
  val account = AwsAccount("testAccountId", "testAccount", "roleArn", "12345")
  val humanWithOneOldEnabledAccessKey =
    HumanUser("amina.adewusi", true, humanAccessKeyOldAndEnabled1, noAccessKey, Green, None, None, Nil)
  val humanWithTwoOldEnabledAccessKeys =
    HumanUser("nic.long", true, humanAccessKeyOldAndEnabled1, humanAccessKeyOldAndEnabled2, Green, None, None, Nil)
  val humanWithHealthyKey =
    HumanUser("jon.soul", true, noAccessKey, humanAccessKeyHealthAndEnabled, Green, None, None, Nil)
  val machineWithOneOldEnabledAccessKey =
    MachineUser("machine1", machineAccessKeyOldAndEnabled, noAccessKey, Green, None, None, Nil)
  val machineWithOneOldEnabledAccessKey2 =
    MachineUser("machine2", machineAccessKeyOldAndEnabledOnTimeThreshold, noAccessKey, Green, None, None, Nil)

  "identifyUsersWithOutdatedCredentials" - {
    val humanAccessKeyOldAndEnabled = AccessKey(AccessKeyEnabled, Some(date.minusMonths(4)))
    val machineAccessKeyOldAndEnabled = AccessKey(AccessKeyEnabled, Some(date.minusMonths(13)))
    val machineAccessKeyOldAndEnabledOnTimeThreshold =
      AccessKey(AccessKeyEnabled, Some(date.minusDays(CoreConfig.iamMachineUserRotationCadence.toInt)))
    val humanEnabledAccessKeyHealthy = AccessKey(AccessKeyEnabled, Some(date.minusMonths(1)))
    val noAccessKey = AccessKey(NoKey, None)
    val account = AwsAccount("testAccountId", "testAccount", "roleArn", "12345")
    val humanWithOneOldEnabledAccessKey =
      HumanUser("amina.adewusi", true, humanAccessKeyOldAndEnabled, noAccessKey, Green, None, None, Nil)
    val humanWithHealthyKey =
      HumanUser("jon.soul", true, noAccessKey, humanEnabledAccessKeyHealthy, Green, None, None, Nil)
    val machineWithOneOldEnabledAccessKey =
      MachineUser("machine1", machineAccessKeyOldAndEnabled, noAccessKey, Green, None, None, Nil)
    val machineWithOneOldEnabledAccessKey2 =
      MachineUser("machine2", machineAccessKeyOldAndEnabledOnTimeThreshold, noAccessKey, Green, None, None, Nil)
    val machineWithOneOldEnabledAccessKeyAndOptOutTag = MachineUser(
      "machine3",
      machineAccessKeyOldAndEnabledOnTimeThreshold,
      noAccessKey,
      Green,
      None,
      None,
      List(Tag(CoreConfig.outdatedCredentialOptOutUserTag, ""))
    )

    "given a vulnerable human user, return that user" in {
      val credsReport = CredentialReportDisplay(date, Seq(), Seq(humanWithOneOldEnabledAccessKey))
      identifyUsersWithOutdatedCredentials(credsReport, date).map(_.username) shouldEqual List("amina.adewusi")
    }
    "given a vulnerable machine user, return that user" in {
      val credsReport = CredentialReportDisplay(date, Seq(machineWithOneOldEnabledAccessKey), Seq())
      identifyUsersWithOutdatedCredentials(credsReport, date).map(_.username) shouldEqual List("machine1")
    }
    "given a vulnerable user with opt-out tag, return an empty list" in {
      val credsReport = CredentialReportDisplay(date, Seq(machineWithOneOldEnabledAccessKeyAndOptOutTag), Seq())
      identifyUsersWithOutdatedCredentials(credsReport, date).map(_.username) shouldBe empty
    }
    "given a vulnerable human and machine user, return both users" in {
      val credsReport =
        CredentialReportDisplay(date, Seq(machineWithOneOldEnabledAccessKey), Seq(humanWithOneOldEnabledAccessKey))
      identifyUsersWithOutdatedCredentials(credsReport, date).map(_.username) should contain allOf (
        "amina.adewusi",
        "machine1"
      )
    }
    "given users with old disabled keys, return an empty list" in {
      val humanWithOneOldDisabledAccessKey = HumanUser(
        "adam.fisher",
        true,
        noAccessKey,
        AccessKey(AccessKeyDisabled, Some(date.minusMonths(4))),
        Green,
        None,
        None,
        Nil
      )
      val machineAccessKeyOldAndDisabled = AccessKey(AccessKeyDisabled, Some(date.minusMonths(13)))
      val machineWithOneOldDisabledAccessKey =
        MachineUser("machine3", noAccessKey, machineAccessKeyOldAndDisabled, Green, None, None, Nil)
      val credsReport =
        CredentialReportDisplay(date, Seq(machineWithOneOldDisabledAccessKey), Seq(humanWithOneOldDisabledAccessKey))
      identifyUsersWithOutdatedCredentials(credsReport, date) shouldBe empty
    }
    "given no users with access keys, return an empty list" in {
      val humanWithNoKeys = HumanUser("jorge.azevedo", true, noAccessKey, noAccessKey, Green, None, None, Nil)
      val credsReport = CredentialReportDisplay(date, Seq(), Seq(humanWithNoKeys))
      identifyUsersWithOutdatedCredentials(credsReport, date) shouldBe empty
    }
    "given no vulnerable access keys, return an empty list" in {
      val machineAccessKeyHealthyAndEnabled = AccessKey(AccessKeyEnabled, Some(date.minusMonths(11)))
      val machineWithHealthyKey =
        MachineUser("machine4", noAccessKey, machineAccessKeyHealthyAndEnabled, Green, None, None, Nil)
      val credsReport = CredentialReportDisplay(date, Seq(machineWithHealthyKey), Seq(humanWithHealthyKey))
      identifyUsersWithOutdatedCredentials(credsReport, date) shouldBe empty
    }
    "given a machine user whose access key was last rotated exactly on the last acceptable healthy rotation date, return user" in {
      val credsReport = CredentialReportDisplay(date, Seq(machineWithOneOldEnabledAccessKey2), Seq())
      identifyUsersWithOutdatedCredentials(credsReport, date) should have length 1
    }
  }

  "calculateOutstandingOperations" - {
    // human activities - warning
    val humanActivityWarningLastNotificationGreaterThanCadence = IamRemediationActivity(
      account.id,
      humanWithOneOldEnabledAccessKey.username,
      date.minusDays(daysBetweenWarningAndFinalNotification + 1),
      Warning,
      OutdatedCredential,
      humanAccessKeyOldAndEnabled1.lastRotated.get
    )
    val humanActivityWarningLastNotificationEqualToCadence = IamRemediationActivity(
      account.id,
      humanWithTwoOldEnabledAccessKeys.username,
      date.minusDays(daysBetweenWarningAndFinalNotification),
      Warning,
      OutdatedCredential,
      humanAccessKeyOldAndEnabled1.lastRotated.get
    )
    // human activities - final
    val humanActivityFinalLastNotificationGreaterThanCadence = IamRemediationActivity(
      account.id,
      humanWithTwoOldEnabledAccessKeys.username,
      date.minusDays(daysBetweenFinalNotificationAndRemediation + 1),
      FinalWarning,
      OutdatedCredential,
      humanAccessKeyOldAndEnabled2.lastRotated.get
    )
    val humanActivityFinalLastNotificationEqualToCadence = IamRemediationActivity(
      account.id,
      humanWithOneOldEnabledAccessKey.username,
      date.minusDays(daysBetweenFinalNotificationAndRemediation),
      FinalWarning,
      OutdatedCredential,
      humanAccessKeyOldAndEnabled1.lastRotated.get
    )
    // human activities - remediation
    val humanActivityRemediationUnhealthy = IamRemediationActivity(
      account.id,
      humanWithOneOldEnabledAccessKey.username,
      date,
      Remediation,
      OutdatedCredential,
      humanAccessKeyOldAndEnabled1.lastRotated.get
    )
    // human users
    val humanBothKeysRequireAction = IamUserRemediationHistory(
      account,
      humanWithTwoOldEnabledAccessKeys,
      List(humanActivityFinalLastNotificationGreaterThanCadence, humanActivityWarningLastNotificationEqualToCadence)
    )
    val humanOneKeyRequiresAction = IamUserRemediationHistory(
      account,
      humanWithOneOldEnabledAccessKey,
      List(humanActivityWarningLastNotificationGreaterThanCadence)
    )
    val humanOneKeyFinalWarning = IamUserRemediationHistory(
      account,
      humanWithOneOldEnabledAccessKey,
      List(humanActivityWarningLastNotificationEqualToCadence, humanActivityFinalLastNotificationEqualToCadence)
    )
    val humanOneKeyRemediation = IamUserRemediationHistory(
      account,
      humanWithOneOldEnabledAccessKey,
      List(
        humanActivityWarningLastNotificationEqualToCadence,
        humanActivityFinalLastNotificationEqualToCadence,
        humanActivityRemediationUnhealthy
      )
    )
    // machine activities - warning
    val machineActivityWarningLastNotificationEqualToCadence = IamRemediationActivity(
      account.id,
      machineWithOneOldEnabledAccessKey.username,
      date.minusDays(daysBetweenWarningAndFinalNotification),
      Warning,
      OutdatedCredential,
      machineAccessKeyOldAndEnabled.lastRotated.get
    )
    val machineActivityWarningKeyLastRotatedEqualCadenceThreshold = IamRemediationActivity(
      account.id,
      machineWithOneOldEnabledAccessKey.username,
      date.minusWeeks(3),
      Warning,
      OutdatedCredential,
      machineAccessKeyOldAndEnabledOnTimeThreshold.lastRotated.get
    )
    // machine access keys
    val machineWithTwoOldEnabledAccessKeys = MachineUser(
      "machine5",
      machineAccessKeyOldAndEnabledOnTimeThreshold,
      machineAccessKeyOldAndEnabled,
      Green,
      None,
      None,
      Nil
    )
    // machine users
    val machineOneKeyWarning = IamUserRemediationHistory(
      account,
      machineWithOneOldEnabledAccessKey,
      List(machineActivityWarningLastNotificationEqualToCadence)
    )
    val machineTwoKeysRequireAction = IamUserRemediationHistory(
      account,
      machineWithTwoOldEnabledAccessKeys,
      List(
        machineActivityWarningLastNotificationEqualToCadence,
        machineActivityWarningKeyLastRotatedEqualCadenceThreshold
      )
    )

    "given two users, each with 2 keys that require operations, output a list of size 4" in {
      calculateOutstandingAccessKeyOperations(
        List(humanBothKeysRequireAction, machineTwoKeysRequireAction),
        date
      ) should have length 4
    }
    "given two users, each with 1 key that requires an operation, output a list of size 2" in {
      calculateOutstandingAccessKeyOperations(
        List(humanOneKeyRequiresAction, machineOneKeyWarning),
        date
      ) should have length 2
    }
    "given one user with 2 keys that require an operation, output a list of size 2" in {
      calculateOutstandingAccessKeyOperations(List(machineTwoKeysRequireAction), date) should have length 2
    }
    "given one user with 1 key that requires an operation, output a list of size 1" in {
      calculateOutstandingAccessKeyOperations(List(humanOneKeyRequiresAction), date) should have length 1
    }
    // this scenario should never happen, keeping this test here to note this.
    "given an empty input list, return an empty output list" in {
      calculateOutstandingAccessKeyOperations(Nil, date) shouldEqual Nil
    }

    "identifyRemediationOperation" - {
      "if there is no previous activity for this key, return a Warning operation" in {
        identifyRemediationOperation(
          mostRecentRemediationActivity = None,
          now = date,
          humanBothKeysRequireAction,
          humanAccessKeyOldAndEnabled1
        ).map(_.iamRemediationActivityType) shouldEqual Some(Warning)
      }
      "if the most recent activity is a Warning with a date more than `Config.daysBetweenWarningAndFinalNotification` days ago, return a Final operation" in {
        val activity = remediationActivity(
          daysBetweenWarningAndFinalNotification + 1,
          Warning,
          humanAccessKeyOldAndEnabled1,
          humanWithOneOldEnabledAccessKey.username
        )
        identifyRemediationOperation(Some(activity), date, humanOneKeyRequiresAction, humanAccessKeyOldAndEnabled1).map(
          _.iamRemediationActivityType
        ) shouldEqual Some(FinalWarning)
      }
      "if the most recent activity is a Warning with a date exactly `Config.daysBetweenWarningAndFinalNotification` days ago, return a Final operation" in {
        val activity = remediationActivity(
          daysBetweenWarningAndFinalNotification,
          Warning,
          machineAccessKeyOldAndEnabled,
          machineWithOneOldEnabledAccessKey.username
        )
        identifyRemediationOperation(Some(activity), date, machineOneKeyWarning, machineAccessKeyOldAndEnabled).map(
          _.iamRemediationActivityType
        ) shouldEqual Some(FinalWarning)
      }
      "if the most recent activity is a Warning with a date less than `Config.daysBetweenWarningAndFinalNotification` days ago, return a None, because no operation is required" in {
        val machineActivityWarningHealthy = IamRemediationActivity(
          account.id,
          machineWithOneOldEnabledAccessKey.username,
          date.minusDays(daysBetweenWarningAndFinalNotification - 1),
          Warning,
          OutdatedCredential,
          machineAccessKeyOldAndEnabled.lastRotated.get
        )
        val machineOneWarningKeyDoesNotRequireAction =
          IamUserRemediationHistory(account, machineWithOneOldEnabledAccessKey, List(machineActivityWarningHealthy))
        val activity = remediationActivity(
          daysBetweenWarningAndFinalNotification - 1,
          Warning,
          machineAccessKeyOldAndEnabled,
          machineWithOneOldEnabledAccessKey.username
        )
        identifyRemediationOperation(
          Some(activity),
          date,
          machineOneWarningKeyDoesNotRequireAction,
          machineAccessKeyOldAndEnabled
        ).map(_.iamRemediationActivityType) shouldBe empty
      }
      "if the most recent activity is a Final with a date more than `Config.daysBetweenFinalNotificationAndRemediation` days ago, return a Remediation operation" in {
        val activity = remediationActivity(
          daysBetweenFinalNotificationAndRemediation + 1,
          FinalWarning,
          humanAccessKeyOldAndEnabled1,
          humanWithTwoOldEnabledAccessKeys.username
        )
        identifyRemediationOperation(Some(activity), date, humanBothKeysRequireAction, humanAccessKeyOldAndEnabled1)
          .map(_.iamRemediationActivityType) shouldEqual Some(Remediation)
      }
      "if the most recent activity is a Final with a date exactly `Config.daysBetweenFinalNotificationAndRemediation` days ago, return a Remediation operation" in {
        val activity = remediationActivity(
          daysBetweenFinalNotificationAndRemediation,
          FinalWarning,
          humanAccessKeyOldAndEnabled1,
          humanWithOneOldEnabledAccessKey.username
        )
        identifyRemediationOperation(Some(activity), date, humanOneKeyFinalWarning, humanAccessKeyOldAndEnabled1).map(
          _.iamRemediationActivityType
        ) shouldEqual Some(Remediation)
      }
      "if the most recent activity is a Final with a date less than `Config.daysBetweenFinalNotificationAndRemediation` days ago, return a None, because no operation is required" in {
        val machineActivityFinalNotificationLessThanCadence = IamRemediationActivity(
          account.id,
          machineWithOneOldEnabledAccessKey.username,
          date.minusDays(daysBetweenFinalNotificationAndRemediation - 1),
          FinalWarning,
          OutdatedCredential,
          machineAccessKeyOldAndEnabled.lastRotated.get
        )
        val machineOneFinalKeyDoesNotRequireAction = IamUserRemediationHistory(
          account,
          machineWithOneOldEnabledAccessKey,
          List(machineActivityFinalNotificationLessThanCadence)
        )
        val activity = remediationActivity(
          daysBetweenFinalNotificationAndRemediation - 1,
          FinalWarning,
          machineAccessKeyOldAndEnabled,
          machineWithOneOldEnabledAccessKey.username
        )
        identifyRemediationOperation(
          Some(activity),
          date,
          machineOneFinalKeyDoesNotRequireAction,
          machineAccessKeyOldAndEnabled
        ).map(_.iamRemediationActivityType) shouldBe empty
      }
      // The most recent activity being a Remediation is an edge case, because it means that the access key has not been successfully disabled.
      // In this edge case, set the operation to Remediation so that Security HQ can try to disable the key again.
      "if the most recent activity is a Remediation, return a Remediation" in {
        val activity =
          remediationActivity(1, Remediation, humanAccessKeyOldAndEnabled1, humanWithOneOldEnabledAccessKey.username)
        identifyRemediationOperation(Some(activity), date, humanOneKeyRemediation, humanAccessKeyOldAndEnabled1).map(
          _.iamRemediationActivityType
        ) shouldEqual Some(Remediation)
      }
      "if the most recent activity is a Warning with a date more than `Config.daysBetweenWarningAndFinalNotification` days ago, return the correct output" in {
        val activity = remediationActivity(
          daysBetweenWarningAndFinalNotification + 1,
          Warning,
          humanAccessKeyOldAndEnabled1,
          humanWithOneOldEnabledAccessKey.username
        )
        val result =
          identifyRemediationOperation(Some(activity), date, humanOneKeyRequiresAction, humanAccessKeyOldAndEnabled1)
        inside(result.value) { case RemediationOperation(candidate, activityType, problem, _) =>
          inside(candidate) { case IamUserRemediationHistory(account, user, _) =>
            account.name shouldEqual "testAccount"
            user.username shouldEqual "amina.adewusi"
          }
          activityType shouldEqual FinalWarning
          problem shouldEqual OutdatedCredential
        }
      }
      "if the most recent activity is a Warning with a date exactly `Config.daysBetweenWarningAndFinalNotification` days ago, return the correct problem creation date" in {
        val activity = remediationActivity(
          daysBetweenWarningAndFinalNotification,
          Warning,
          machineAccessKeyOldAndEnabled,
          machineWithOneOldEnabledAccessKey.username
        )
        val result =
          identifyRemediationOperation(Some(activity), date, machineOneKeyWarning, machineAccessKeyOldAndEnabled)
        result.map(_.problemCreationDate) shouldEqual Some(machineAccessKeyOldAndEnabled.lastRotated.get)
      }
    }

    "identifyMostRecentIamRemediationActivity" - {
      "if the key's most recent activity is Warning, return Warning" in {
        val key = machineAccessKeyOldAndEnabled
        val activity = remediationActivity(
          daysBetweenWarningAndFinalNotification,
          Warning,
          key,
          machineWithOneOldEnabledAccessKey.username
        )
        val history = IamUserRemediationHistory(account, machineWithOneOldEnabledAccessKey, List(activity))
        identifyMostRecentActivity(history, key, date).map(_.iamRemediationActivityType) shouldEqual Some(Warning)
      }
      "if the key's most recent activity is Final, return Final" in {
        val key = humanAccessKeyOldAndEnabled1
        val activity = remediationActivity(
          daysBetweenFinalNotificationAndRemediation,
          FinalWarning,
          key,
          humanWithOneOldEnabledAccessKey.username
        )
        val history = IamUserRemediationHistory(account, humanWithOneOldEnabledAccessKey, List(activity))
        identifyMostRecentActivity(history, key, date).map(_.iamRemediationActivityType) shouldEqual Some(FinalWarning)
      }
      "if the key's most recent activity is Remediation, return Remediation" in {
        val key = humanAccessKeyOldAndEnabled1
        val activity = remediationActivity(1, Remediation, key, humanWithOneOldEnabledAccessKey.username)
        val history = IamUserRemediationHistory(account, humanWithOneOldEnabledAccessKey, List(activity))
        identifyMostRecentActivity(history, key, date).map(_.iamRemediationActivityType) shouldEqual Some(Remediation)
      }
      "given a key does not have any activity, return None" in {
        val activity = Nil
        val machineNoActivity = IamUserRemediationHistory(account, machineWithOneOldEnabledAccessKey2, activity)
        identifyMostRecentActivity(machineNoActivity, machineAccessKeyOldAndEnabledOnTimeThreshold, date) shouldBe empty
      }
      "if the key's most recent activity is Warning, return the correct output" in {
        val key = machineAccessKeyOldAndEnabled
        val activity = remediationActivity(
          daysBetweenWarningAndFinalNotification,
          Warning,
          key,
          machineWithOneOldEnabledAccessKey.username
        )
        val history = IamUserRemediationHistory(account, machineWithOneOldEnabledAccessKey, List(activity))
        val result = identifyMostRecentActivity(history, key, date)
        inside(result.value) {
          case IamRemediationActivity(
                awsAccountId,
                username,
                _,
                iamRemediationActivityType,
                iamProblem,
                problemCreationDate
              ) =>
            awsAccountId shouldEqual "testAccountId"
            username shouldEqual "machine1"
            iamRemediationActivityType shouldEqual Warning
            iamProblem shouldEqual OutdatedCredential
        }
      }
      "if the key's most recent activity is Final, return the correct date the last notification was sent" in {
        val key = humanAccessKeyOldAndEnabled1
        val activity = remediationActivity(
          daysBetweenFinalNotificationAndRemediation,
          FinalWarning,
          key,
          humanWithOneOldEnabledAccessKey.username
        )
        val history = IamUserRemediationHistory(account, humanWithOneOldEnabledAccessKey, List(activity))
        identifyMostRecentActivity(history, key, date).map(_.dateNotificationSent) shouldEqual Some(
          date.minusDays(daysBetweenFinalNotificationAndRemediation)
        )
      }
    }

    "identifyOutstandingAccessKeyOperations must handle gaps gracefully" - {
      val username = "ancientMachine"
      // Key was created two years ago.  It is ancient.
      val twoYearsAgo = date.minusMonths(24)
      val aYearAgo = date.minusMonths(12)
      val machineUser = MachineUser(
        username,
        AccessKey(AccessKeyEnabled, Some(twoYearsAgo)),
        AccessKey(AccessKeyEnabled, Some(aYearAgo)),
        Green,
        None,
        None,
        Nil
      )
      val aYearAndABit = aYearAgo.minusDays(daysBetweenWarningAndFinalNotification)
      def warning(warningDate: DateTime) = IamRemediationActivity(
        account.id,
        username,
        warningDate,
        Warning,
        OutdatedCredential,
        twoYearsAgo
      )
      def finalWarning(finalWarningDate: DateTime) = IamRemediationActivity(
        account.id,
        username,
        finalWarningDate,
        FinalWarning,
        OutdatedCredential,
        twoYearsAgo
      )

      "given a key that was given a final warning months ago, but the task has not run since, go back to warning" in {
        // Key was issued a final warning a year ago.
        // Key was issued a warning a year ago.
        val remediationOperation = calculateOutstandingAccessKeyOperations(
          List(
            IamUserRemediationHistory(
              account,
              machineUser,
              List(
                warning(aYearAndABit),
                finalWarning(aYearAgo)
              )
            )
          ),
          date
        ).head
        remediationOperation.iamProblem shouldBe OutdatedCredential
        remediationOperation.iamRemediationActivityType shouldBe Warning
      }

      "given a key that was given a final warning 13 days ago, but the task has not run since, carry on" in {
        // Key was issued a final warning a year ago.
        val aYearAgo = date.minusMonths(12)
        // Key was issued a warning 14 days ago
        val aYearAndFifteenDaysAgo = date.minusDays(15)
        // Key was issued a final warning 13 days ago
        val aYearAndThirteenDaysAgo = date.minusDays(13)
        val remediationOperation = calculateOutstandingAccessKeyOperations(
          List(
            IamUserRemediationHistory(
              account,
              machineUser,
              List(
                warning(aYearAndFifteenDaysAgo),
                finalWarning(aYearAndThirteenDaysAgo)
              )
            )
          ),
          date
        ).head
        remediationOperation.iamProblem shouldBe OutdatedCredential
        remediationOperation.iamRemediationActivityType shouldBe Remediation
      }

      "given a key that was given a final warning 14 days ago, but the task has not run since, go back to warning" in {
        // Key was issued a final warning a year ago.
        val aYearAgo = date.minusMonths(12)
        // Key was issued a warning 14 days ago
        val aYearAndFifteenDaysAgo = date.minusDays(15)
        // Key was issued a final warning 13 days ago
        val aYearAndfourteenDaysAgo = date.minusDays(14)
        val remediationOperation = calculateOutstandingAccessKeyOperations(
          List(
            IamUserRemediationHistory(
              account,
              machineUser,
              List(
                warning(aYearAndFifteenDaysAgo),
                finalWarning(aYearAndfourteenDaysAgo)
              )
            )
          ),
          date
        ).head
        remediationOperation.iamProblem shouldBe OutdatedCredential
        remediationOperation.iamRemediationActivityType shouldBe Warning
      }
    }

    "identifyVulnerableKeys" - {
      "given a human user with 1 vulnerable access key, return that key" in {
        identifyVulnerableKeys(humanOneKeyRequiresAction, date).map(_.lastRotated) shouldEqual List(
          humanAccessKeyOldAndEnabled1.lastRotated
        )
      }
      "given a machine user with 1 vulnerable access key, return that key" in {
        identifyVulnerableKeys(machineOneKeyWarning, date).map(_.lastRotated) shouldEqual List(
          machineAccessKeyOldAndEnabled.lastRotated
        )
      }
      "given 2 vulnerable access keys, return both keys" in {
        identifyVulnerableKeys(humanBothKeysRequireAction, date).map(_.lastRotated) shouldEqual List(
          humanAccessKeyOldAndEnabled1.lastRotated,
          humanAccessKeyOldAndEnabled2.lastRotated
        )
      }
      // this scenario should never happen, becuase this function should only be called if there is at least one problem access key.
      "given no vulnerable access keys, return an empty list" in {
        val humanActivityRemediationHealthy = IamRemediationActivity(
          account.id,
          humanWithHealthyKey.username,
          date.minusMonths(2),
          Remediation,
          OutdatedCredential,
          humanAccessKeyHealthAndEnabled.lastRotated.get
        )
        val humanHealthy =
          IamUserRemediationHistory(account, humanWithHealthyKey, List(humanActivityRemediationHealthy))
        identifyVulnerableKeys(humanHealthy, date) shouldBe empty
      }
    }
  }

  "partitionOperationsByAllowedAccounts" - {
    val operationsForAccountA = operationForAccountId("a", "machineUser1")
    val operationsForAccountB = operationForAccountId("b", "machineUser2")
    val operationsForAccountC = operationForAccountId("c", "machineUser3")
    val operations = List(operationsForAccountA, operationsForAccountB, operationsForAccountC)
    val serviceAccounts = List("a", "b", "c")

    "if allowedAccounts is empty" - {
      val allowedAccounts = Nil
      "then all operations are not allowed" in {
        val forbidden = partitionOperationsByAllowedAccounts(
          operations,
          allowedAccounts,
          serviceAccounts
        ).operationsOnAccountsThatAreNotAllowed
        forbidden shouldEqual operations
      }
      "then allowed operations is empty" in {
        val allowed =
          partitionOperationsByAllowedAccounts(operations, allowedAccounts, serviceAccounts).allowedOperations
        allowed shouldEqual Nil
      }
    }

    "if there is one allowed account provided" - {
      val allowedAccounts = List("a")
      "then all operations that don't match provided allowed account are forbidden" in {
        val forbidden = partitionOperationsByAllowedAccounts(
          operations,
          allowedAccounts,
          serviceAccounts
        ).operationsOnAccountsThatAreNotAllowed
        forbidden shouldEqual List(operationsForAccountB, operationsForAccountC)
      }
      "then allowed operations matches provided allowed account" in {
        val allowed =
          partitionOperationsByAllowedAccounts(operations, allowedAccounts, serviceAccounts).allowedOperations
        allowed shouldEqual List(operationsForAccountA)
      }
      "and it is not an account that is a client of the remediation service, allowed operations is empty" in {
        val allowed =
          partitionOperationsByAllowedAccounts(operations, allowedAccounts, List("b", "c")).allowedOperations
        allowed shouldEqual Nil
      }
    }

    "if multiple allowed accounts are provided" - {
      val allowedAccounts = List("a", "b")
      "then all operations that don't match any allowed accounts are forbidden" in {
        val forbidden = partitionOperationsByAllowedAccounts(
          operations,
          allowedAccounts,
          serviceAccounts
        ).operationsOnAccountsThatAreNotAllowed
        forbidden shouldEqual List(operationsForAccountC)
      }
      "then matching operations are allowed" in {
        val allowed =
          partitionOperationsByAllowedAccounts(operations, allowedAccounts, serviceAccounts).allowedOperations
        allowed shouldEqual List(operationsForAccountA, operationsForAccountB)
      }
    }

    "if operations to partition is empty" - {
      val allowedAccounts = List("a", "b")
      "then forbidden operations should also be empty" in {
        val forbidden = partitionOperationsByAllowedAccounts(
          Nil,
          allowedAccounts,
          serviceAccounts
        ).operationsOnAccountsThatAreNotAllowed
        forbidden shouldEqual Nil
      }
      "then allowed operations should also be empty" in {
        val allowed = partitionOperationsByAllowedAccounts(Nil, allowedAccounts, serviceAccounts).allowedOperations
        allowed shouldEqual Nil
      }
    }
  }

  "lookupCredentialId" - {
    val nonMatchingAccessKey =
      CredentialMetadata("adam.fisher", "AKIAIOSFODNN1EXAMPLE", date.minusDays(1), CredentialActive)
    val matchingAccessKey = CredentialMetadata("amina.adewusi", "AKIAIOSFODNN2EXAMPLE", date, CredentialActive)
    val matchingAccessKey2 = CredentialMetadata("amina.adewusi", "AKIAIOSFODNN3EXAMPLE", date, CredentialActive)

    "given a key creation date matches a date in the metadata, return the correct metadata" in {
      val result = lookupCredentialId(date, List(matchingAccessKey, nonMatchingAccessKey))
      result.value().username shouldEqual matchingAccessKey.username
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
    // A key's last rotated date has resolution to the second, so this function must match up to the second, but not the millisecond.
    "given a key creation date matches to the minute, but not the second, return a failure" in {
      val keyCreationDate = new DateTime(2021, 1, 1, 1, 1, 1)
      val metaDataCreationDate = new DateTime(1, 1, 1, 1, 1, 2)
      val result =
        lookupCredentialId(keyCreationDate, List(matchingAccessKey.copy(creationDate = metaDataCreationDate)))
      result.isFailedAttempt() shouldBe true
    }
    "given a key creation date matches to the second, but not the millisecond, return the metadata because we do not expect millisecond resolution" in {
      val keyCreationDate = new DateTime(1, 1, 1, 1, 1, 1, 1)
      val metaDataCreationDate = new DateTime(1, 1, 1, 1, 1, 1, 2)
      val result =
        lookupCredentialId(keyCreationDate, List(matchingAccessKey.copy(creationDate = metaDataCreationDate)))
      result.value().username shouldEqual matchingAccessKey.username
    }
  }

  "formatRemediationOperation" - {
    val date = new DateTime(2021, 1, 1, 1, 1)
    val account = AwsAccount("testAccountId", "testAccount", "roleArn", "12345")
    val humanUser =
      HumanUser("human.user", hasMFA = true, AccessKey(NoKey, None), AccessKey(NoKey, None), Green, None, None, Nil)
    val machineUser =
      MachineUser("machine.user", AccessKey(NoKey, None), AccessKey(NoKey, None), Green, None, None, Nil)

    "should return a readable message for OutdatedCredentials" in {
      val iamUserRemediationHistory = IamUserRemediationHistory(account, machineUser, Nil)
      val operation = RemediationOperation(iamUserRemediationHistory, FinalWarning, OutdatedCredential, date)
      formatRemediationOperation(
        operation
      ) shouldEqual "OutdatedCredential FinalWarning for user machine.user from account testAccountId"
    }
  }

  "performRemediationOperation" - {

    val fakeRemediationSnsClient = new SnsAsyncClient {
      private var notificationCount = 0

      override def publish(publishRequest: PublishRequest): CompletableFuture[PublishResponse] = {
        notificationCount += 1
        CompletableFuture.completedFuture(PublishResponse.builder().messageId(s"sns-$notificationCount").build())
      }

      override def serviceName(): String = "sns"

      override def close(): Unit = ()
    }

    val fakeRemediationIamClient = new IamAsyncClient {
      override def listAccessKeys(request: ListAccessKeysRequest): CompletableFuture[ListAccessKeysResponse] = {
        val accessKeyMetadata = AccessKeyMetadata
          .builder()
          .userName(request.userName())
          .accessKeyId("TEST_KEY")
          .status(StatusType.ACTIVE)
          .createDate(Instant.ofEpochMilli(machineAccessKeyOldAndEnabled.lastRotated.get.getMillis))
          .build()
        CompletableFuture.completedFuture(
          ListAccessKeysResponse.builder().accessKeyMetadata(accessKeyMetadata).build()
        )
      }

      override def updateAccessKey(
          request: software.amazon.awssdk.services.iam.model.UpdateAccessKeyRequest
      ): CompletableFuture[UpdateAccessKeyResponse] =
        CompletableFuture.completedFuture(UpdateAccessKeyResponse.builder().build())

      override def serviceName(): String = "iam"

      override def close(): Unit = ()
    }

    val fakeRemediationDb = new IamRemediationDb(null) {
      override def writeRemediationActivity(iamRemediationActivity: IamRemediationActivity, tableName: String)(implicit
          ec: scala.concurrent.ExecutionContext
      ): Attempt[String] = Attempt.Right("fake-dynamo-write-id")
    }

    def getIamOutdatedCredentials(dryRun: Boolean) = new IamOutdatedCredentials(
      snsClient = fakeRemediationSnsClient,
      iamClients = List(AwsClient(fakeRemediationIamClient, account, Region.of("us-east-1"))),
      dynamo = fakeRemediationDb,
      dryRun = dryRun
    )

    val fakeTopicArn = "arn:aws:sns:eu-west-1:123456789012:test-topic"
    val fakeRemediationTableName = "test-remediation-table"
    val securityAccount = Some(AwsAccount("security", "Security", "role", "999999999999"))
    def getOperation(IamRemediationActivityType: IamRemediationActivityType) = RemediationOperation(
      IamUserRemediationHistory(account, machineWithOneOldEnabledAccessKey, Nil),
      IamRemediationActivityType,
      OutdatedCredential,
      machineAccessKeyOldAndEnabled.lastRotated.get
    )

    "in dry run mode" - {
      val dryRun = true
      "should return no notifications for warning" in {
        val result = getIamOutdatedCredentials(dryRun).performRemediationOperation(
          getOperation(Warning),
          new DateTime(),
          fakeTopicArn,
          fakeRemediationTableName,
          devXSecurityAccountMaybe = securityAccount
        )
        result.value() shouldEqual Nil
      }

      "should return no notifications for final warning" in {
        val result = getIamOutdatedCredentials(dryRun).performRemediationOperation(
          getOperation(FinalWarning),
          new DateTime(),
          fakeTopicArn,
          fakeRemediationTableName,
          devXSecurityAccountMaybe = securityAccount
        )
        result.value() shouldEqual Nil
      }

      "should return no notifications for remediation" in {
        val result = getIamOutdatedCredentials(dryRun).performRemediationOperation(
          getOperation(Remediation),
          new DateTime(),
          fakeTopicArn,
          fakeRemediationTableName,
          devXSecurityAccountMaybe = securityAccount
        )
        result.value() shouldEqual Nil
      }
    }

    "not in dry run mode" - {
      val dryRun = false
      "should return one notification for warning" in {
        val result = getIamOutdatedCredentials(dryRun).performRemediationOperation(
          getOperation(Warning),
          new DateTime(),
          fakeTopicArn,
          fakeRemediationTableName,
          devXSecurityAccountMaybe = securityAccount
        )
        result.value().length shouldBe 1
        result.value().head shouldBe "sns-1"
      }

      "should return one notification for final warning" in {
        val result = getIamOutdatedCredentials(dryRun).performRemediationOperation(
          getOperation(FinalWarning),
          new DateTime(),
          fakeTopicArn,
          fakeRemediationTableName,
          devXSecurityAccountMaybe = securityAccount
        )
        result.value().length shouldBe 1
        result.value().head shouldBe "sns-2"
      }

      "should return two notifications for remediation" in {
        val result = getIamOutdatedCredentials(dryRun).performRemediationOperation(
          getOperation(Remediation),
          new DateTime(),
          fakeTopicArn,
          fakeRemediationTableName,
          devXSecurityAccountMaybe = securityAccount
        )
        result.value().length shouldBe 2
        result.value().head shouldBe "sns-3"
        result.value().tail.head shouldBe "sns-4"

      }
    }
  }

  def operationForAccountId(id: String, username: String): RemediationOperation = {
    val machineUser = MachineUser(username, AccessKey(NoKey, None), AccessKey(NoKey, None), Green, None, None, Nil)
    RemediationOperation(
      IamUserRemediationHistory(AwsAccount(id, "", "", ""), machineUser, Nil),
      Warning,
      OutdatedCredential,
      new DateTime()
    )
  }
  def remediationActivity(
      dayOffset: Int,
      activityType: IamRemediationActivityType,
      accessKey: AccessKey,
      username: String
  ) =
    IamRemediationActivity(
      account.id,
      username,
      date.minusDays(dayOffset),
      activityType,
      OutdatedCredential,
      accessKey.lastRotated.get
    )
}
