package services

import aws.AwsClients
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.sns.AmazonSNSAsync
import config.Config.{getAllowedAccountsForStage, getAnghammaradSnsTopic, getIamDynamoTableName}
import logic.VulnerableIamUser.getCredsReportDisplayForAccount
import play.api.Configuration
import utils.attempt.Attempt

import scala.concurrent.ExecutionContext


class IamRemediationService(
  cacheService: CacheService, snsClient: AmazonSNSAsync, dynamo: AwsDynamoAlertService,
  config: Configuration, iamClients: AwsClients[AmazonIdentityManagementAsync]
) {

  def disableOutdatedCredentials()(implicit ec: ExecutionContext): Attempt[Unit] = {
    val result = for {
      // lookup essential configuration
      topicArn <- getAnghammaradSnsTopic(config)
      tableName <- getIamDynamoTableName(config)
      allowedAwsAccounts <- getAllowedAccountsForStage(config)  // this tells us which AWS accounts we are allowed to make changes to
      // fetch IAM data from the application cache
      rawCredsReports = cacheService.getAllCredentials
      accountsCredReports = getCredsReportDisplayForAccount(rawCredsReports)


      // identify vulnerable users for each account, from its credentials report
//      accountVulnerableUsers = accountCredsReports.map { case (account, display) => (account, getAccountVulnerableUsers(display)) } // test
      // check SHQ's database for previous activity for these users
      //      accountVulnerableUsers = identifyVulnerableUsers(accountCredsReports)
      //      db <- Attempt.traverse(accountVulnerableUsers){ case (account, vulnerableUser) =>
      //        dynamo.lookupVulnerableUser(vulnerableUser, account).map((account -> _))
      //      }


//      accountCandidateVulnerableUsersForDynamo = accountDynamoRequests(accountVulnerableUsers, tableName) // test
//      getFromDynamo <- Attempt.tupleTraverse(accountCandidateVulnerableUsersForDynamo)(dynamo.get)
//      accountIamAuditUsers = getIamAuditUsers(getFromDynamo) // test
//      // decide whether we should perform any operations
//      dynamoUsersWithDeadline = vulnerableUsersWithDynamoDeadline(accountIamAuditUsers) // test
//      operations = triageCandidates(dynamoUsersWithDeadline) // test
//      _ <- Attempt.traverse(operations)(performOperation(_, topicArn, testMode, tableName))

    } yield ()
    result
  }

  def removePasswordWithoutMFA(): Attempt[Unit] = ???
  def deleteFormerStaff(): Attempt[Unit] = ???
}
