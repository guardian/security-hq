package schedule.vulnerable

import aws.AwsAsyncHandler.{awsToScala, handleAWSErrs}
import aws.AwsClients
import aws.iam.IAMClient.SOLE_REGION
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{UpdateAccessKeyRequest, UpdateAccessKeyResult}
import logging.Cloudwatch
import logging.Cloudwatch.ReaperExecutionStatus
import logic.VulnerableAccessKeys.isOutdated
import model.{AwsAccount, VulnerableAccessKey, VulnerableUser}
import play.api.Logging
import schedule.vulnerable.IamListAccessKeys.listAccountAccessKeys
import utils.attempt.Attempt

import scala.concurrent.ExecutionContext

object IamDisableAccessKeys extends Logging {

  def disableAccessKeys(
    account: AwsAccount,
    vulnerableUsers: List[VulnerableUser],
    iamClients: AwsClients[AmazonIdentityManagementAsync]
  )(implicit ec: ExecutionContext): Attempt[List[UpdateAccessKeyResult]] = {
    val result = for {
      accessKeys <- listAccountAccessKeys(account, vulnerableUsers, iamClients)
      outdatedAccessKeys = getOutdatedKeys(accessKeys)
      updateAccessKeyRequests = outdatedAccessKeys.map(updateAccessKeyRequest)
      client <- iamClients.get(account, SOLE_REGION)
      updateAccessKeyResults <- Attempt.traverse(updateAccessKeyRequests)(req => handleAWSErrs(client)(awsToScala(client)(_.updateAccessKeyAsync)(req)))
    } yield updateAccessKeyResults
    result.fold(
      { failure =>
        logger.error(s"Failed to disable access key: ${failure.logMessage}")
        Cloudwatch.putIamDisableAccessKeyMetric(ReaperExecutionStatus.failure)
      },
      { success =>
        logger.info(s"Successfully disabled access key in ${account.name}.")
        Cloudwatch.putIamDisableAccessKeyMetric(ReaperExecutionStatus.success)
      }
    )
    result
  }

  private def getOutdatedKeys(keys: List[VulnerableAccessKey]): List[VulnerableAccessKey] = keys.filter(isOutdated)
  private def updateAccessKeyRequest(key: VulnerableAccessKey): UpdateAccessKeyRequest = {
    new UpdateAccessKeyRequest()
      .withUserName(key.username)
      .withAccessKeyId(key.accessKeyWithId.id)
      .withStatus("Inactive")
  }
}
