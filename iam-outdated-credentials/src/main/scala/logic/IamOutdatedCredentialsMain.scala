package logic

import aws.AWS
import aws.iam.IAMClient
import config.CoreConfig.{calculateAvailableRegions, getSecurityDynamoDbClient}
import db.IamRemediationDb
import model.{AwsAccount, CredentialReportDisplay, DEV, PROD}
import settings.Settings
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sns.SnsAsyncClient
import utils.attempt.FailedAttempt

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}

object IamOutdatedCredentialsMain {

  implicit val ec: ExecutionContext = ExecutionContext.global

  def main(args: Array[String]): Unit = args.toList match {
    case stack
        :: stageString
        :: dryRunString
        :: anghammaradSnsArn
        :: iamDynamoTableName
        :: iamUnrecognisedUserS3Bucket
        :: iamUnrecognisedUserS3Key
        :: allowedAccountIds
        :: accountIdsForIamRemediationService
        :: configBucket
        :: configKey
        :: Nil =>

      val settings = {
        val stage = if (stageString.equalsIgnoreCase("prod")) PROD else DEV
        val dryRun = dryRunString.equalsIgnoreCase("true")
        Settings(
          stack = stack,
          stage = stage,
          dryRun = dryRun,
          anghammaradSnsArn = anghammaradSnsArn,
          iamDynamoTableName = iamDynamoTableName,
          iamUnrecognisedUserS3Bucket = iamUnrecognisedUserS3Bucket,
          iamUnrecognisedUserS3Key = iamUnrecognisedUserS3Key,
          allowedAccountIds = allowedAccountIds.split(",").toList,
          accountIdsForIamRemediationService = accountIdsForIamRemediationService.split(",").toList,
          configBucket = configBucket,
          configKey = configKey
        )
      }
      val snsClient = SnsAsyncClient.builder().build()

      val s3Client = S3Client.builder.build()
      val awsAccountsConfig =
        IamOutdatedCredentials.loadConfigFromS3(settings.configBucket, settings.configKey, s3Client)
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
      val credentialReportFutures: Seq[Future[(AwsAccount, Either[FailedAttempt, CredentialReportDisplay])]] =
        awsAccounts
          .map(account =>
            IAMClient
              .getUpdatedCredentialsReport(account, cfnClients, iamClients, availableRegions, delay)
              .asFuture
              .map(result => account -> result)
          )

      val timeout = 3.minutes
      val eventualList: Future[Seq[(AwsAccount, Either[FailedAttempt, CredentialReportDisplay])]] =
        Future.sequence(credentialReportFutures)

      val listOfCredentialReports: Map[AwsAccount, Either[FailedAttempt, CredentialReportDisplay]] =
        Await.result(eventualList, timeout).toMap

      val result =
        job
          .disableOutdatedCredentials(
            notificationTopicArn = settings.anghammaradSnsArn,
            tableName = settings.iamDynamoTableName,
            serviceAccountIds = settings.accountIdsForIamRemediationService,
            rawCredsReports = listOfCredentialReports,
            allowedAwsAccountIds = settings.allowedAccountIds
          )
          .asFuture

      Await.result(result, 10.minutes) match {
        case Right(_) =>
          println("Completed successfully")

        case Left(err) =>
          Console.err.println(s"Job failed: $err")
          sys.exit(1)
      }
    case _ =>
      println(
        "Usage: sbt 'project iam-outdated-credentials; runMain logic/IamOutdatedCredentialsMain " +
          "   <stack>" +
          "   <stage>" +
          "   <dryRunFlag>" +
          "   <anghammaradSnsArn>" +
          "   <tableName>" +
          "   <accountId>" +
          "   <accountName>" +
          "   <roleArn>" +
          "   <accountNumber>'"
      )
  }

}
