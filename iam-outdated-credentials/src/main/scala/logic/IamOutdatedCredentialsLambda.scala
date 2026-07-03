package logic

import aws.AWS
import aws.iam.IAMClient
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import config.CoreConfig.{calculateAvailableRegions, getSecurityDynamoDbClient}
import db.IamRemediationDb
import model.{AwsAccount, CredentialReportDisplay}
import settings.Settings
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sns.SnsAsyncClient
import utils.attempt.FailedAttempt

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.ListHasAsScala

class IamOutdatedCredentialsLambda
  extends RequestHandler[Map[String, String], String] {

  implicit val ec: ExecutionContext = ExecutionContext.global

  override def handleRequest(
                              event: Map[String, String],
                              context: Context
                            ): String = {

    val settings = Settings.fromEnvironment()

    val snsClient = SnsAsyncClient.builder().build()

    val s3Client = S3Client.builder.build()
    val awsAccountsConfig = IamOutdatedCredentials.loadConfigFromS3(settings.configBucket, settings.configKey, s3Client)
    val awsAccounts = IamOutdatedCredentials.parseAccounts(awsAccountsConfig)


    // the aim of this is to get all the regions that are available to this account
    val availableRegions: List[Region] = calculateAvailableRegions(settings.stack, settings.stage)

    val iamClients = AWS.iamClients(awsAccounts, availableRegions)

    val dynamo =
      new IamRemediationDb(getSecurityDynamoDbClient(settings.stage))

    val job = new IamOutdatedCredentials(
      snsClient = snsClient,
      iamClients = iamClients,
      dynamo = dynamo,
      dryRun = settings.dryRun
    )

    val cfnClients = AWS.cfnClients(awsAccounts, availableRegions)
    val delay = 3.seconds
    val credentialReportFutures: Seq[Future[(AwsAccount, Either[FailedAttempt, CredentialReportDisplay])]] = awsAccounts
      .map(account =>
        IAMClient.getUpdatedCredentialsReport(account, cfnClients, iamClients, availableRegions, delay).asFuture
          .map(result => account -> result))

    val timeout = 3.minutes
    val eventualList: Future[Seq[(AwsAccount, Either[FailedAttempt, CredentialReportDisplay])]] = Future.sequence(credentialReportFutures)

    val listOfCredentialReports: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] = Await.result(eventualList, timeout).toMap

    val result =
      job.disableOutdatedCredentials(
        notificationTopicArn = settings.anghammaradSnsArn,
        tableName = settings.iamDynamoTableName,
        serviceAccountIds = settings.accountIdsForIamRemediationService,
        rawCredsReports = listOfCredentialReports,
        allowedAwsAccountIds = settings.allowedAccountIds
      ).asFuture

    Await.result(result, 10.minutes) match {
      case Right(_) =>
        "Success"

      case Left(err) =>
        throw new RuntimeException(err.toString)
    }
  }
}