package schedule.vulnerable

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs, recoverAWSErrs}
import aws.AwsClients
import aws.iam.IAMClient.SOLE_REGION
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{DeleteLoginProfileRequest, DeleteLoginProfileResult}
import logging.Cloudwatch
import logging.Cloudwatch.ReaperExecutionStatus
import model.{AwsAccount, VulnerableUser}
import play.api.Logging
import utils.attempt.Attempt

import scala.concurrent.ExecutionContext

object IamRemovePassword extends Logging {

  def removePasswords(
    account: AwsAccount,
    user: VulnerableUser,
    iamClients: AwsClients[AmazonIdentityManagementAsync]
  )(implicit ec: ExecutionContext): Attempt[Option[DeleteLoginProfileResult]] = {
    if (user.humanUser) {
      val recoverableErrors: PartialFunction[Throwable, Option[DeleteLoginProfileResult]] = {
        case e if e.getMessage.contains(s"Login Profile for User ${user.username} cannot be found") => None
      }
      val result: Attempt[Option[DeleteLoginProfileResult]] = for {
        client <- iamClients.get(account, SOLE_REGION)
        request = new DeleteLoginProfileRequest().withUserName(user.username)
        deleteResult <- recoverAWSErrs(client, recoverableErrors)(awsToScala(client)(_.deleteLoginProfileAsync)(request).map(Some(_)))
      } yield deleteResult
      result.fold(
        { failure =>
          logger.error(s"failed to delete password for username: ${user.username}. ${failure.logMessage}")
          Cloudwatch.putIamRemovePasswordMetric(ReaperExecutionStatus.failure)
        },
        { success =>
          logger.info(s"password deleted for ${user.username}. DeleteLoginProfile Response: ${success.map(_.getSdkResponseMetadata.getRequestId)}.")
          Cloudwatch.putIamRemovePasswordMetric(ReaperExecutionStatus.success)
        }
      )
      result
    } else {
      logger.info(s"will not attempt to remove password, because this ${user.username} is a machine user.")
      Attempt.Right(None)
    }
  }
}
