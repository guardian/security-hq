package schedule.vulnerable

import aws.AwsAsyncHandler.awsToScala
import aws.iam.IAMClient.SOLE_REGION
import aws.{AwsClient, AwsClients}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.DeleteLoginProfileRequest
import logging.Cloudwatch
import model.{AwsAccount, VulnerableUser}
import play.api.Logging

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object IamRemovePassword extends Logging {

  def removePasswords(
    account: AwsAccount,
    users: Seq[VulnerableUser],
    iamClients: AwsClients[AmazonIdentityManagementAsync]
  )(implicit ec: ExecutionContext): Unit = {
    users.map { user =>
      iamClients.get(account, SOLE_REGION).map { client =>
        deleteUserLoginProfile(client, user)
      }
    }
  }

  private def deleteUserLoginProfile(
    client: AwsClient[AmazonIdentityManagementAsync],
    user: VulnerableUser
  )(implicit ec: ExecutionContext): Unit = {
    val request = new DeleteLoginProfileRequest().withUserName(user.username)
    awsToScala(client)(_.deleteLoginProfileAsync)(request).onComplete {
      case Failure(exception) =>
        logger.warn(s"failed to delete password for username: ${user.username}.", exception)
        Cloudwatch.putIamRemovePasswordMetric(1)
        // TODO trigger cloudwatch alarm for failure case
      case Success(result) =>
        logger.info(s"successfully deleted password for username: ${user.username}. DeleteLoginProfile Response: ${result.toString}.")
        Cloudwatch.putIamRemovePasswordMetric(0)
    }
  }
}
