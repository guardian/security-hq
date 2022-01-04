package schedule.vulnerable

import aws.AwsAsyncHandler.awsToScala
import aws.iam.IAMClient.SOLE_REGION
import aws.{AwsAsyncHandler, AwsClient, AwsClients}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{DeleteLoginProfileRequest, DeleteLoginProfileResult, NoSuchEntityException}
import logging.Cloudwatch
import logging.Cloudwatch.ReaperExecutionStatus
import model.{AwsAccount, VulnerableUser}
import play.api.Logging
import utils.attempt.Attempt

import scala.concurrent.{ExecutionContext, Future}

object IamRemovePassword extends Logging {

  def removePasswords(
    account: AwsAccount,
    user: VulnerableUser,
    iamClients: AwsClients[AmazonIdentityManagementAsync]
  )(implicit ec: ExecutionContext): Attempt[Option[DeleteLoginProfileResult]] = {
    if (user.humanUser) {
      val result: Attempt[Option[DeleteLoginProfileResult]] = for {
        client <- iamClients.get(account, SOLE_REGION)
        request = new DeleteLoginProfileRequest().withUserName(user.username)
        response = awsToScala(client)(_.deleteLoginProfileAsync)(request)
        deleteResult <- handleAWSErrs(client, user)(response)
      } yield deleteResult
      result.tap {
          case Left(failure) =>
            logger.error(s"failed to delete password for username: ${user.username}. ${failure.logMessage}")
            Cloudwatch.putIamRemovePasswordMetric(ReaperExecutionStatus.failure)
          case Right(success) =>
            logger.info(s"password deleted for ${user.username}. DeleteLoginProfile Response: ${success.map(_.getSdkResponseMetadata.getRequestId)}.")
            Cloudwatch.putIamRemovePasswordMetric(ReaperExecutionStatus.success)
        }
    } else {
      logger.info(s"will not attempt to remove password, because this ${user.username} is a machine user.")
      Attempt.Right(None)
    }
  }

  def handleAWSErrs(awsClient: AwsClient[AmazonIdentityManagementAsync], user: VulnerableUser)(f: => Future[DeleteLoginProfileResult])(implicit ec: ExecutionContext): Attempt[Option[DeleteLoginProfileResult]] =
    AwsAsyncHandler.handleAWSErrs(awsClient)(f.map(Some.apply).recover({
      case e if e.getMessage.contains(s"Login Profile for User ${user.username} cannot be found") => None
      case _: NoSuchEntityException => None
    }))
}