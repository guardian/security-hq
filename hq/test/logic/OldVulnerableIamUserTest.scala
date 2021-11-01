package logic

import awscala.dynamodbv2.AttributeValue
import logic.OldVulnerableIamUser._
import model._
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}
import utils.attempt.AttemptValues

class OldVulnerableIamUserTest extends FreeSpec with Matchers with AttemptValues with AttributeValues {

  val date = new DateTime(2021, 1, 1, 1, 1)
  // access keys
  val oldKeyEnabledHuman = AccessKey(AccessKeyEnabled, Some(DateTime.now.minusMonths(6)))
  val noKey = AccessKey(NoKey, None)
  val oldKeyEnabledMachine = AccessKey(AccessKeyEnabled, Some(DateTime.now.minusMonths(13)))
  // human users
  val humanMissingMfa = HumanUser("amina.adewusi", hasMFA = false, noKey, noKey, Red(Seq(MissingMfa)), None, None, List.empty)
  val humanWithAnOldAccessKey = HumanUser("adam.fisher", hasMFA = true , oldKeyEnabledHuman, noKey, Red(Seq(OutdatedKey)), None, None, List.empty)
  val humanWithTwoOldAccessKeys = HumanUser("jorge.azevedo", hasMFA = true , oldKeyEnabledHuman, oldKeyEnabledHuman, Red(Seq(OutdatedKey)), None, None, List.empty)
  val humanMissingMfaWithTwoOldAccessKeys = HumanUser("mark.butler", hasMFA = false , oldKeyEnabledHuman, oldKeyEnabledHuman, Red(Seq(OutdatedKey)), None, None, List.empty)
  //machine users
  val machineWithOldKey = MachineUser("machine1", oldKeyEnabledMachine, noKey, Green, None, None, List.empty)
  val machineWithTwoOldKeys = MachineUser("machine1", oldKeyEnabledMachine, oldKeyEnabledMachine, Green, None, None, List.empty)
  // audit user
  val awsAccount1 = AwsAccount("id", "accountName", "roleArn", "12345")
  val awsAccount2 = AwsAccount("id", "accountName", "roleArn", "67890")
  val alerts = List(IamAuditAlert(VulnerableCredential, date, date))
  val auditUser = IamAuditUser(awsAccount1.id + humanMissingMfa.username, "account", "username", alerts)
  val tableName = "SecurityHQDynamoTable"
  // vulnerable user
  val vulnerableHumanUser = VulnerableUser(humanMissingMfa.username, humanMissingMfa.key1, humanMissingMfa.key2, humanUser = true, Nil, None)

  "getAccountVulnerableUsers" - {

    "detect human user missing mfa, but with healthy access keys" in {
      val credentialReportDisplay = CredentialReportDisplay(date, Seq.empty, Seq(humanMissingMfa))
      getAccountVulnerableUsers(credentialReportDisplay) should have length 1
    }
    "detect human user with mfa, but with one old access key" in {
      val credentialReportDisplay = CredentialReportDisplay(date, Seq.empty, Seq(humanWithAnOldAccessKey))
      getAccountVulnerableUsers(credentialReportDisplay) should have length 1
    }
    "detect human user with mfa, but with two old access keys" in {
      val credentialReportDisplay = CredentialReportDisplay(date, Seq.empty, Seq(humanWithTwoOldAccessKeys))
      getAccountVulnerableUsers(credentialReportDisplay) should have length 1
    }
    "detect a human user with both missing mfa and an old access key" in {
      val credentialReportDisplay = CredentialReportDisplay(date, Seq.empty, Seq(humanMissingMfaWithTwoOldAccessKeys))
      getAccountVulnerableUsers(credentialReportDisplay) should have length 1
    }
    "detect machine user with one old access key" in {
      val credentialReportDisplay = CredentialReportDisplay(date, Seq(machineWithOldKey), Seq.empty)
      getAccountVulnerableUsers(credentialReportDisplay) should have length 1
    }
    "detect a machine user with two old access keys" in {
      val credentialReportDisplay = CredentialReportDisplay(date, Seq(machineWithTwoOldKeys), Seq.empty)
      getAccountVulnerableUsers(credentialReportDisplay) should have length 1
    }
    "detect problems with both human and machine users" in {
      val credentialReportDisplay = CredentialReportDisplay(date, Seq(machineWithTwoOldKeys), Seq(humanMissingMfaWithTwoOldAccessKeys))
      getAccountVulnerableUsers(credentialReportDisplay) should have length 2
    }
    "return an empty list when there are no users with old access keys or missing mfa" in {
      val credentialReportDisplay = CredentialReportDisplay(date, Seq.empty, Seq.empty)
      getAccountVulnerableUsers(credentialReportDisplay) shouldBe empty
    }
  }
  "accountDynamoRequests" - {
    "given one account and one user, return the account and one GetItemRequest" in {
      val accountVulnerableUser = List((awsAccount1, List(vulnerableHumanUser)))
      accountDynamoRequests(accountVulnerableUser, tableName) should have length 1
      }
    "given one account and one user, return correct GetItemRequest for that user" ignore {
      val accountVulnerableUser = List((awsAccount1, List(vulnerableHumanUser)))
      accountDynamoRequests(accountVulnerableUser, tableName) shouldEqual 1 //TODO not sure how to write the GetItemRequest
      }
    "given one account and two users, return a list of two elements" in {
      val accountVulnerableUser = List((awsAccount1, List(vulnerableHumanUser, vulnerableHumanUser)))
      accountDynamoRequests(accountVulnerableUser, tableName) should have length 2
    }
    "given two accounts with one user each, return a list of two elements" in {
      val accountVulnerableUser = List((awsAccount1, List(vulnerableHumanUser)), (awsAccount1, List(vulnerableHumanUser)))
      accountDynamoRequests(accountVulnerableUser, tableName) should have length 2
    }
    "an account with an empty list does not appear in the response" in {
      val accountVulnerableUser = List((awsAccount1, Nil), (awsAccount1, List(vulnerableHumanUser)))
      accountDynamoRequests(accountVulnerableUser, tableName) should have length 1
    }
    "given only one account with an empty list of users, return an empty list" in {
      val accountVulnerableUser = List((awsAccount1, Nil))
      accountDynamoRequests(accountVulnerableUser, tableName) shouldBe empty
    }
    "given an empty input list, return an empty list" in {
      accountDynamoRequests(Nil, tableName) shouldBe empty
    }
  }
  "getIamAuditUsers" - {
    val key = Map(
      awsAccount1.id -> new AttributeValue(Some(awsAccount1.id + humanMissingMfa.username)),
      "awsAccount" -> new AttributeValue(Some("account")),
      "username" -> new AttributeValue(Some("username")),
      "alerts" -> new AttributeValue(Some("alerts")),
    )
    "given a database response, return the correct IamAuditUser" ignore {
      val input = List((awsAccount1, vulnerableHumanUser, key))
      getIamAuditUsers(input).head._3 shouldBe Some(auditUser) //TODO I think key is incorrect - something wrong with the alerts.
    }
    "given two database responses, associates IamAuditUsers with correct Aws Account" ignore {} //TODO
    "given an empty input list, return an empty output list" in {
      getIamAuditUsers(Nil) shouldBe empty
    }
    "if database response is missing required field, does not return IamAuditUser" in {
      val input = List((awsAccount1, vulnerableHumanUser, Map("awsAccount" -> new AttributeValue(Some("account")))))
      getIamAuditUsers(input).head._3 shouldBe None
    }
  }

  "vulnerableUsersWithDynamoDeadline" - {
    val accountVulnerableUserAndAuditUser1 = (awsAccount1, vulnerableHumanUser, Some(auditUser))
    val accountVulnerableUserAndAuditUser2 = (awsAccount2, vulnerableHumanUser, Some(auditUser))
    "given one account with one IamAuditUser, return one account with one VulnerableUserWithDeadline" in {
      vulnerableUsersWithDynamoDeadline(List(accountVulnerableUserAndAuditUser1)) should have length 1
    }
    "given one account with two IamAuditUsers, return one account with two VulnerableUserWithDeadlines" in {
      val input = List(accountVulnerableUserAndAuditUser1, accountVulnerableUserAndAuditUser1)
      vulnerableUsersWithDynamoDeadline(input) should have length 2
    }
    "given two accounts with one IamAuditUser each, return two accounts with one VulnerableUserWithDeadline each" in {
      val input = List(accountVulnerableUserAndAuditUser1, accountVulnerableUserAndAuditUser2)
      vulnerableUsersWithDynamoDeadline(input) should have length 2
    }
    "given an empty input list, return an empty list" in {
      vulnerableUsersWithDynamoDeadline(Nil) shouldBe empty
    }
    //TODO discuss these tests with Adam - all testing the deadline logic
    "given no IamAuditUser, return a deadline" ignore {}
    "given no IamAuditUser, return the correct deadline" ignore {}
    "given an IamAuditUser with one alert, return a deadline" ignore {}
    "given an IamAuditUser with multiple alerts, return the nearest deadline" ignore {}
  }

  //TODO add something that catches the cron job not running on a certain day
  "triageCandidates" - {
    val warningDeadline = DateTime.now().plusWeeks(3)
    val finalDeadline = DateTime.now().plusDays(1)
    val disableDeadline = DateTime.now()
    val warningUser = VulnerableUserWithDeadline(humanMissingMfa.username, humanMissingMfa.key1, humanMissingMfa.key2, humanUser = true, Nil, warningDeadline)
    val finalUser = VulnerableUserWithDeadline(humanMissingMfa.username, humanMissingMfa.key1, humanMissingMfa.key2, humanUser = true, Nil, finalDeadline)
    val disableUser = VulnerableUserWithDeadline(humanMissingMfa.username, humanMissingMfa.key1, humanMissingMfa.key2, humanUser = true, Nil, disableDeadline)
    //TODO not sure how to write test which output is of type WarningAlert?
    "given user to be warned, output WarningAlert" in {
      val input = List((awsAccount1, warningUser, Some(auditUser)))
      triageCandidates(input).head._2 shouldEqual WarningAlert(auditUser)
    }
    "given user to receive final alert, output FinalAlert" in {
      val input = List((awsAccount1, finalUser, Some(auditUser)))
      triageCandidates(input).head._2 shouldEqual FinalAlert(auditUser)
    }
    "given user to be disabled, output disable operation" in {
      val input = List((awsAccount1, disableUser, Some(auditUser)))
      triageCandidates(input).head._2 shouldEqual Disable("amina.adewusi", "TODO") //TODO hardcoding this at present. Need to fix.
    }
    "given two accounts, with a user each, return both accounts with their correct users" in {
      val input = List((awsAccount1, disableUser, Some(auditUser)), (awsAccount2, finalUser, Some(auditUser)))
      triageCandidates(input) should have length 2
    }
    "given an empty list, return an empty list" in {
      triageCandidates(Nil) shouldBe empty
    }
  }
}
