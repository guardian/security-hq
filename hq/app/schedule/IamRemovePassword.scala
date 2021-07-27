package schedule

import aws.AwsAsyncHandler.awsToScala
import aws.iam.IAMClient.SOLE_REGION
import aws.{AwsClient, AwsClients}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.DeleteLoginProfileRequest
import model.{AwsAccount, VulnerableUser}
import play.api.Logging

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object IamRemovePassword extends Logging {

  def deleteUserLoginProfile(client: AwsClient[AmazonIdentityManagementAsync], user: VulnerableUser)(implicit ec: ExecutionContext): Unit = {
    val request = new DeleteLoginProfileRequest().withUserName(user.username)
    awsToScala(client)(_.deleteLoginProfileAsync)(request).onComplete {
      case Failure(exception) => logger.warn(s"failed to delete password for username: ${user.username}.", exception)
      case Success(result) => logger.info(s"successfully deleted password for username: ${user.username}. DeleteLoginProfile Response: ${result.toString}.")
    }
  }

  def removePasswords(account: AwsAccount, users: Seq[VulnerableUser], iamClients: AwsClients[AmazonIdentityManagementAsync])
    (implicit ec: ExecutionContext): Unit = {
    users.map { user =>
      iamClients.get(account, SOLE_REGION).map { client =>
        deleteUserLoginProfile(client, user)
      }
    }
  }
}
