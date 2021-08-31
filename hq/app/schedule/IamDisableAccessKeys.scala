package schedule

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.iam.IAMClient.SOLE_REGION
import aws.{AwsClient, AwsClients}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{UpdateAccessKeyRequest, UpdateAccessKeyResult}
import com.amazonaws.services.sns.AmazonSNSAsync
import com.gu.anghammarad.models.{AwsAccount => Account}
import logic.VulnerableAccessKeys.isOutdated
import model._
import play.api.Logging
import schedule.IamListAccessKeys.listAccountAccessKeys
import schedule.IamMessages.{disabledAccessKeyMessage, disabledAccessKeySubject}
import schedule.IamNotifier.{notification, send}
import utils.attempt.Attempt

import scala.concurrent.ExecutionContext

object IamDisableAccessKeys extends Logging {

  def disableAccessKeys(
    account: AwsAccount,
    vulnerableUsers: Seq[VulnerableUser],
    iamClients: AwsClients[AmazonIdentityManagementAsync],
    topicArn: Option[String],
    snsClient: AmazonSNSAsync,
    testMode: Boolean
  )
    (implicit ec: ExecutionContext): Unit = {
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
          notifyAwsAccount(account, user.username, key.id, topicArn, snsClient, testMode)
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
      handleAWSErrs(client)(awsToScala(client)(_.updateAccessKeyAsync)(request))
    }

  private def notifyAwsAccount(
    account: AwsAccount,
    username: String,
    accessKeyId: String,
    topicArn: Option[String],
    snsClient: AmazonSNSAsync,
    testMode: Boolean
  )(implicit ec: ExecutionContext): Unit = {
    send(
      notification(disabledAccessKeySubject(username, account), disabledAccessKeyMessage(username, accessKeyId), List(Account(account.accountNumber))),
      topicArn,
      snsClient,
      testMode
    )
  }
}
