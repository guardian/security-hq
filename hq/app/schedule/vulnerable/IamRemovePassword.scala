package schedule.vulnerable

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.iam.IAMClient
import aws.{AwsClient, AwsClients}
import aws.iam.IAMClient.SOLE_REGION
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{DeleteLoginProfileRequest, DeleteLoginProfileResult}
import logging.Cloudwatch
import logging.Cloudwatch.ReaperExecutionStatus
import model.{AwsAccount, VulnerableUser}
import play.api.Logging
import utils.attempt.Failure.contextString
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.concurrent.{ExecutionContext, Future}

object IamRemovePassword extends Logging {

  // TODO: move this into logic package and use consistent model
  def removePasswords(
    account: AwsAccount,
    user: VulnerableUser,
    iamClients: AwsClients[AmazonIdentityManagementAsync]
  )(implicit ec: ExecutionContext): Attempt[Option[DeleteLoginProfileResult]] = {
    if (user.humanUser) {
      val result: Attempt[Option[DeleteLoginProfileResult]] = for {
        client <- iamClients.get(account, SOLE_REGION)
        deleteResult <- recoverAcceptableFailures(IAMClient.deleteLoginProfile(user.username, client), user, client)
      } yield deleteResult

      result.tap {
        case Left(failedAttempt) =>
          logger.error(s"failed to delete password for username: ${user.username}. ${failedAttempt.logMessage}")
          Cloudwatch.putIamRemovePasswordMetric(ReaperExecutionStatus.failure)

        case Right(result) =>
          logger.info(s"password deleted for ${user.username}. DeleteLoginProfile Response: ${result.map(_.getSdkResponseMetadata.getRequestId)}.")
          Cloudwatch.putIamRemovePasswordMetric(ReaperExecutionStatus.success)
      }
    } else {
      logger.info(s"will not attempt to remove password, because ${user.username} is a machine user.")
      Attempt.Right(None)
    }
  }

  def recoverAcceptableFailures(
    attempt: Attempt[DeleteLoginProfileResult],
    user: VulnerableUser,
    client: AwsClient[AmazonIdentityManagementAsync]
  )(implicit executionContext: ExecutionContext): Attempt[Option[DeleteLoginProfileResult]] =
    attempt.partialRecover({
      case f if f == IAMClient.Failures.noLoginProfileFailure(user.username, client).attempt => None
    }, Some(_))
}
