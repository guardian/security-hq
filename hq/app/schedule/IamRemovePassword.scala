package schedule

import aws.AwsAsyncHandler.awsToScala
import aws.iam.IAMClient.SOLE_REGION
import aws.{AwsClient, AwsClients}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.DeleteLoginProfileRequest
import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.models.{AwsAccount => Account}
import model.{AwsAccount, VulnerableUser}
import play.api.Logging
import schedule.IamMessages.{passwordRemovedMessage, passwordRemovedSubject}
import schedule.IamNotifier.{notification, send}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object IamRemovePassword extends Logging {

  def removePasswords(
    account: AwsAccount,
    users: Seq[VulnerableUser],
    iamClients: AwsClients[AmazonIdentityManagementAsync],
    topicArn: Option[String],
    snsClient: AmazonSNSAsync,
    testMode: Boolean
  )
    (implicit ec: ExecutionContext): Unit = {
    users.map { user =>
      iamClients.get(account, SOLE_REGION).map { client =>
        deleteUserLoginProfile(client, user, topicArn, snsClient, testMode)
      }
    }
  }

  private def deleteUserLoginProfile(
    client: AwsClient[AmazonIdentityManagementAsync],
    user: VulnerableUser,
    topicArn: Option[String],
    snsClient: AmazonSNSAsync,
    testMode: Boolean
  )(implicit ec: ExecutionContext): Unit = {
    val request = new DeleteLoginProfileRequest().withUserName(user.username)
    awsToScala(client)(_.deleteLoginProfileAsync)(request).onComplete {
      case Failure(exception) => logger.warn(s"failed to delete password for username: ${user.username}.", exception)
      case Success(result) =>
        logger.info(s"successfully deleted password for username: ${user.username}. DeleteLoginProfile Response: ${result.toString}.")
        notify(client, user, topicArn, snsClient, testMode)
    }
  }

  private def notify(
    client: AwsClient[AmazonIdentityManagementAsync],
    user: VulnerableUser,
    topicArn: Option[String],
    snsClient: AmazonSNSAsync,
    testMode: Boolean)(implicit ec: ExecutionContext): Unit = {
    send(
      notification(passwordRemovedSubject(user, client), passwordRemovedMessage(user), List(Account(client.account.accountNumber))),
      topicArn,
      snsClient,
      testMode
    )
  }
}
