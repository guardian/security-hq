package schedule.vulnerable

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.iam.IAMClient.SOLE_REGION
import aws.{AwsClient, AwsClients}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{UpdateAccessKeyRequest, UpdateAccessKeyResult}
import logic.VulnerableAccessKeys.isOutdated
import model.{AccessKeyWithId, AwsAccount, VulnerableAccessKey, VulnerableUser}
import play.api.Logging
import schedule.vulnerable.IamListAccessKeys.listAccountAccessKeys
import utils.attempt.Attempt

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object IamDisableAccessKeys extends Logging {

  def disableAccessKeys(
    account: AwsAccount,
    vulnerableUsers: Seq[VulnerableUser],
    iamClients: AwsClients[AmazonIdentityManagementAsync]
  )(implicit ec: ExecutionContext): Unit = {
    // this does the work of taking our vulnerable users who have been flagged as potentially needing their access keys disabled
    // and converts that vulnerableUser into a user that has it's access key id attached to it
    val vulnerableUserWithAccessKeyId: Attempt[List[VulnerableAccessKey]] = listAccountAccessKeys(account, vulnerableUsers, iamClients)
    vulnerableUserWithAccessKeyId.fold ({ failure =>
      logger.warn(s"about to disable access keys of vulnerable users, but unable to: ${failure.failures.map(_.friendlyMessage)}")
    },  users =>
      users.filter(isOutdated).map { user =>
        val key = user.accessKeyWithId
        logger.info(s"attempting to disable access key id ${key.id}.")
        for {
          client <- iamClients.get(account, SOLE_REGION)
          updateAccessKeyResult <- disableAccessKey(key, client, user.username)
        } yield {
          val updateAccessKeyRequestId = updateAccessKeyResult.getSdkResponseMetadata.getRequestId
          logger.info(s"disabled access key for ${user.username} with access key id ${key.id} and request id: $updateAccessKeyRequestId.")
        }
      }
    )
  }

  private def disableAccessKey(key: AccessKeyWithId, client: AwsClient[AmazonIdentityManagementAsync], username: String)
    (implicit ec: ExecutionContext): Attempt[UpdateAccessKeyResult] = {
      val request = new UpdateAccessKeyRequest()
        .withUserName(username)
        .withAccessKeyId(key.id)
        .withStatus("Inactive")
      val eventualResult: Future[UpdateAccessKeyResult] = awsToScala(client)(_.updateAccessKeyAsync)(request)
      eventualResult.onComplete {
        case Failure(exception) =>
          logger.warn(s"failed to disable access key id ${key.id} for user $username.", exception)
        // TODO trigger cloudwatch alarm for failure case
        case Success(result) =>
          logger.info(s"successfully disabled access key id ${key.id} for user $username. Response: ${result.toString}.")
      }
    handleAWSErrs(client)(eventualResult)
  }
}
