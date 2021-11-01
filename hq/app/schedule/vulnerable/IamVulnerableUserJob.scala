package schedule.vulnerable

import aws.AwsClients
import aws.iam.IAMClient.SOLE_REGION
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.sns.AmazonSNSAsync
import config.Config.{getAnghammaradSnsTopic, getIamDynamoTableName}
import logic.OldVulnerableIamUser._
import logic.VulnerableIamUser.getCredsReportDisplayForAccount
import model._
import play.api.{Configuration, Logging}
import schedule.Notifier.send
import schedule.{AwsDynamoAlertService, CronSchedules, JobRunner}
import services.CacheService
import utils.attempt.Attempt

import scala.concurrent.ExecutionContext

class IamVulnerableUserJob(cacheService: CacheService, snsClient: AmazonSNSAsync, dynamo: AwsDynamoAlertService, config: Configuration, iamClients: AwsClients[AmazonIdentityManagementAsync])(implicit val executionContext: ExecutionContext) extends JobRunner with Logging {
  override val id = "vulnerable-iam-users"
  override val description = "Automated notifications and disablement of vulnerable permanent credentials"
  override val cronSchedule: CronSchedule = CronSchedules.everyWeekDay

  def run(testMode: Boolean): Unit = {
    if (testMode) logger.info(s"Skipping scheduled $id job as it is not enabled")
    else logger.info(s"Running scheduled job: $description")

    for {
      topicArn <- getAnghammaradSnsTopic(config)
      dynamoTable <- getIamDynamoTableName(config)
      credsReports = cacheService.getAllCredentials
      accountCredsReports = getCredsReportDisplayForAccount(credsReports)
      accountVulnerableUser = accountCredsReports.map { case (account, display) => (account, getAccountVulnerableUsers(display)) } // test
      accountCandidateVulnerableUsersForDynamo = accountDynamoRequests(accountVulnerableUser, dynamoTable) // test
      getFromDynamo <- Attempt.tupleTraverse(accountCandidateVulnerableUsersForDynamo)(dynamo.get)
      accountIamAuditUsers = getIamAuditUsers(getFromDynamo) // test
      // consider Map or write in tests we expect 1 account per list users. If Map, we can write MapTraverse
      dynamoUsersWithDeadline = vulnerableUsersWithDynamoDeadline(accountIamAuditUsers) // test
      operations = triageCandidates(dynamoUsersWithDeadline) // test
      _ <- Attempt.traverse(operations)(performOperation(_, topicArn, testMode, dynamoTable))
    } yield ()
  }

  // TODO what happens when one of these operations fails? Atm essential dependent steps. Might not be appropriate.
  def performOperation(accountOperations: (AwsAccount, VulnerableUserOperation), topicArn: String, testMode: Boolean, table: String): Attempt[String] = {
    val (account, operation) = accountOperations
    operation match {
      case w: WarningAlert =>
        for {
          sendId <- send(generateNotifications(account, w), topicArn, snsClient, testMode)
          request = putRequest(w.user, table)
          putResult <- dynamo.put(request)
          putResultId = putResult.getSdkResponseMetadata.getRequestId
        } yield sendId ++ putResultId
      case f: FinalAlert =>
        for {
          sendId <- send(generateNotifications(account, f), topicArn, snsClient, testMode)
          request = putRequest(f.user, table)
          putResult <- dynamo.put(request)
          putResultId = putResult.getSdkResponseMetadata.getRequestId
        } yield sendId ++ putResultId
      case disableUser @ Disable(username, accessKeyId) =>
        for {
          client <- iamClients.get(account, SOLE_REGION)
          _ <- disableAccessKey(account, username, accessKeyId, client.client)
          _ <- removePassword(account, username, client.client)
          id <- send(generateNotifications(account, disableUser), topicArn, snsClient, testMode)
        } yield id
    }
  }
}
