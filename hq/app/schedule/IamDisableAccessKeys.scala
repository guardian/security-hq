package schedule

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.iam.IAMClient.SOLE_REGION
import aws.{AwsClient, AwsClients}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{UpdateAccessKeyRequest, UpdateAccessKeyResult}
import model._
import play.api.Logging
import schedule.IamFlaggedUsers.{hasOutdatedHumanKey, hasOutdatedMachineKey}
import schedule.IamListAccessKeys.listAccountAccessKeys
import utils.attempt.Attempt

import scala.concurrent.ExecutionContext

object IamDisableAccessKeys extends Logging {

  def disableAccessKeys(account: AwsAccount, vulnerableUsers: Seq[VulnerableUser], iamClients: AwsClients[AmazonIdentityManagementAsync])
    (implicit ec: ExecutionContext): Unit = {
    // this does the work of taking our vulnerable users who have been flagged as potentially needing their access keys disabled
    // and converts that vulnerableUser into a user that has it's access key id attached to it
    val vulnerableUserWithAccessKeyId: Attempt[List[VulnerableUserWithAccessKeyId]] = listAccountAccessKeys(account, vulnerableUsers, iamClients)
    vulnerableUserWithAccessKeyId.map { users =>
      findAccessKeysToDisable(users).map { user =>
        val key = user.accessKey
        logger.info(s"attempting to disable access key id ${key.id}.")
        for {
          client <- iamClients.get(account, SOLE_REGION)
          updateAccessKeyResult <- disableAccessKey(key, client)
        } yield {
          val updateAccessKeyRequestId = updateAccessKeyResult.getSdkResponseMetadata.getRequestId
          logger.info(s"tried to disable access key id ${key.id} with request id: $updateAccessKeyRequestId.")
        }
      }
    }
  }

  def findAccessKeysToDisable(users: List[VulnerableUserWithAccessKeyId]): List[VulnerableUserWithAccessKeyId] = {
    users.filter { user =>
      //TODO create a Boolean field (isOutdated) on AccessKey which does the following
      if (user.humanUser) user.accessKey.keyStatus == AccessKeyEnabled && hasOutdatedHumanKey(List(user.accessKey))
      else user.accessKey.keyStatus == AccessKeyEnabled && hasOutdatedMachineKey(List(user.accessKey))
    }
  }

  def disableAccessKey(key: AccessKeyWithId, client: AwsClient[AmazonIdentityManagementAsync])
    (implicit ec: ExecutionContext): Attempt[UpdateAccessKeyResult] = {
      val request = new UpdateAccessKeyRequest()
      .withAccessKeyId(key.id)
      .withStatus("Inactive")
      handleAWSErrs(client)(awsToScala(client)(_.updateAccessKeyAsync)(request))
    }
}
