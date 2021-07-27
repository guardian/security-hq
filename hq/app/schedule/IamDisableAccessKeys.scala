package schedule

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.iam.IAMClient.SOLE_REGION
import aws.{AwsClient, AwsClients}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{UpdateAccessKeyRequest, UpdateAccessKeyResult}
import model.{AccessKeyData, AccessKeyEnabled, AwsAccount, VulnerableUser}
import play.api.Logging
import schedule.IamFlaggedUsers.{hasOutdatedHumanKey, hasOutdatedMachineKey}
import schedule.IamListAccessKeys.{getAccessKeyData, listAccountAccessKeys}
import utils.attempt.Attempt

import scala.concurrent.ExecutionContext

object IamDisableAccessKeys extends Logging {

  def disableAccessKeys(account: AwsAccount, users: Seq[VulnerableUser], iamClients: AwsClients[AmazonIdentityManagementAsync])
    (implicit ec: ExecutionContext): Unit = {
    val keysToDisable = findAccessKeysToDisable(getAccessKeyData(listAccountAccessKeys(account, users, iamClients), users))
    keysToDisable.map { keys =>
      keys.map { key =>
        logger.info(s"attempting to disable access key id ${key.accessKeyId} for username: ${key.username}.")
        for {
          client <- iamClients.get(account, SOLE_REGION)
          updateAccessKeyResult <- disableAccessKey(key, client)
        } yield {
          val updateAccessKeyRequestId = updateAccessKeyResult.getSdkResponseMetadata.getRequestId
          logger.info(s"tried to disable access key id ${key.accessKeyId} for username: ${key.username}.")
        }
      }
    }
  }

  def findAccessKeysToDisable(accessKeyData: (Attempt[List[AccessKeyData]], Seq[VulnerableUser]))
    (implicit ec: ExecutionContext): Attempt[List[AccessKeyData]] = {
    val (keyDataAtt, users) = accessKeyData
    keyDataAtt.map { keyData =>
      keyData.filter { accessKey =>
        filterHumanAccessKeys(accessKey, users) || filterMachineAccessKeys(accessKey, users)
      }
    }
  }

  def filterHumanAccessKeys(accessKeyData: AccessKeyData, users: Seq[VulnerableUser]): Boolean = {
    val filteredHumanUsers: Seq[VulnerableUser] = users.filter { user =>
    user.humanUser && // is this access key owned by a human? (rather than a machine)
      user.key1.keyStatus == AccessKeyEnabled || user.key2.keyStatus == AccessKeyEnabled && // is this access key enabled?
      hasOutdatedHumanKey(List(user.key1, user.key2)) // does this access key need rotating?
    }
    // does the given accessKeyData have a username equal to one in the list of filtered human users?
    filteredHumanUsers.map(_.username).contains(accessKeyData.username)
  }

  def filterMachineAccessKeys(accessKeyData: AccessKeyData, users: Seq[VulnerableUser]): Boolean = {
    val filteredMachineUsers: Seq[VulnerableUser] = users.filter { user =>
      !user.humanUser &&
        user.key1.keyStatus == AccessKeyEnabled || user.key2.keyStatus == AccessKeyEnabled &&
        hasOutdatedMachineKey(List(user.key1, user.key2))
    }
    filteredMachineUsers.map(_.username).contains(accessKeyData.username)
  }

  def disableAccessKey(key: AccessKeyData, client: AwsClient[AmazonIdentityManagementAsync])
    (implicit ec: ExecutionContext): Attempt[UpdateAccessKeyResult] = {
    val request = new UpdateAccessKeyRequest()
      .withAccessKeyId(key.accessKeyId)
      .withUserName(key.username)
      .withStatus("Inactive")
    handleAWSErrs(client)(awsToScala(client)(_.updateAccessKeyAsync)(request))
  }
}
