package unrecognised

import aws.AwsClient
import model.{AccountUnrecognisedAccessKeys, AwsAccount, CredentialActive, CredentialMetadata}
import org.joda.time.DateTime
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.iam.IamAsyncClient
import software.amazon.awssdk.services.iam.model.{AccessKeyMetadata, ListAccessKeysRequest, ListAccessKeysResponse, StatusType, UpdateAccessKeyResponse}
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.{PublishRequest, PublishResponse}
import utils.attempt.{Attempt, AttemptValues}

import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class UnrecognisedUsersTest extends AnyFreeSpec with Matchers with OptionValues with AttemptValues {

  private def getIamAsyncClient: (AtomicInteger, IamAsyncClient) = {
    val disabledKeyCount: AtomicInteger = new AtomicInteger(0)
    val iamAsyncClient = new IamAsyncClient {
      override def listAccessKeys(request: ListAccessKeysRequest): CompletableFuture[ListAccessKeysResponse] = {
        val accessKeyMetadata = AccessKeyMetadata
          .builder()
          .userName(request.userName())
          .accessKeyId("TEST_KEY")
          .status(StatusType.ACTIVE)
          .createDate(Instant.now())
          .build()
        CompletableFuture.completedFuture(
          ListAccessKeysResponse.builder().accessKeyMetadata(accessKeyMetadata).build()
        )
      }

      override def updateAccessKey(
                                    request: software.amazon.awssdk.services.iam.model.UpdateAccessKeyRequest
                                  ): CompletableFuture[UpdateAccessKeyResponse] = {
        disabledKeyCount.incrementAndGet()
        CompletableFuture.completedFuture(UpdateAccessKeyResponse.builder().build())
      }

      override def serviceName(): String = "iam"

      override def close(): Unit = ()
    }
    (disabledKeyCount, iamAsyncClient)
  }

  def getFakeRemediationSnsClient: SnsAsyncClient = new SnsAsyncClient {
    private var notificationCount = 0

    override def publish(publishRequest: PublishRequest): CompletableFuture[PublishResponse] = {
      notificationCount += 1
      CompletableFuture.completedFuture(PublishResponse.builder().messageId(s"sns-$notificationCount").build())
    }

    override def serviceName(): String = "sns"

    override def close(): Unit = ()
  }

  val fakeTopicArn = "arn:aws:sns:eu-west-1:123456789012:test-topic"

  def getUnrecognisedUsers(
      dryRun: Boolean,
      awsAccounts: Option[AwsAccount],
      janusUsernames: List[String],
      allowedAccountIds: List[String],
  ): (AtomicInteger, AtomicInteger, AtomicInteger, AtomicInteger, UnrecognisedUsers) = {
    val successMetricRemovePasswordCounter: AtomicInteger = new AtomicInteger(0)
    val failureMetricRemovePasswordCounter: AtomicInteger = new AtomicInteger(0)
    val successMetricDisableAccessKeyCounter: AtomicInteger = new AtomicInteger(0)
    val failureMetricDisableAccessKeyCounter: AtomicInteger = new AtomicInteger(0)
    val unrecognisedUsers = new UnrecognisedUsers(
      awsAccounts.toList,
      janusUsernames,
      allowedAccountIds,
      dryRun,
      fakeTopicArn,
      snsClient = getFakeRemediationSnsClient
    ) {
      override private[unrecognised] def failureMetricRemovePassword[T](using ExecutionContext) = {
        failureMetricRemovePasswordCounter.incrementAndGet()
        Attempt.Right(())
      }

      override private[unrecognised] def successMetricRemovePassword[T](using ExecutionContext) = {
        successMetricRemovePasswordCounter.incrementAndGet()
        Attempt.Right(())
      }

      override private[unrecognised] def failureMetricIamDisableAccessKey[T](using ExecutionContext) = {
        failureMetricDisableAccessKeyCounter.incrementAndGet()
        Attempt.Right(())
      }

      override private[unrecognised] def successMetricIamDisableAccessKey[T](using ExecutionContext) = {
        successMetricDisableAccessKeyCounter.incrementAndGet()
        Attempt.Right(())
      }
    }
    (successMetricRemovePasswordCounter, failureMetricRemovePasswordCounter, successMetricDisableAccessKeyCounter, failureMetricDisableAccessKeyCounter, unrecognisedUsers)
  }

  val account = AwsAccount("testAccountId", "testAccount", "roleArn", "12345")

  "disableAccountAccessKeys" - {
    "in dry run mode should" - {
      val dryRun = true

      "handle disabling a key" in {
        val awsAccounts = None
        val (sRemovePassword, fRemovePassword, sDisableKey, fDisableKey, unrecognisedUsers) = getUnrecognisedUsers(dryRun, awsAccounts = awsAccounts, janusUsernames = List.empty, allowedAccountIds = List.empty)
        val keys = AccountUnrecognisedAccessKeys(
          account = account,
          vulnerableAccessKey = List(CredentialMetadata(
            username = "testuser",
            accessKeyId = "testAccessKey",
            creationDate = new DateTime(),
            status = CredentialActive
          ))
        )
        unrecognisedUsers.disableAccountAccessKeys(
          accountUnrecognisedKeys = keys,
          iamClients = awsAccounts.map(c => AwsClient(c, account, Region.of("us-east-1"))).toList
        )
        sRemovePassword.get() shouldBe 0
        fRemovePassword.get() shouldBe 0
        sDisableKey.get() shouldBe 0
        fDisableKey.get() shouldBe 0
      }
    }
    "not in dry run mode should" - {
      val dryRun = false

      "handle disabling a key when the account is not present with an error metric" in {
        val awsAccounts = None
        val (sRemovePassword, fRemovePassword, sDisableKey, fDisableKey, unrecognisedUsers) = getUnrecognisedUsers(dryRun, awsAccounts = awsAccounts, janusUsernames = List.empty, allowedAccountIds = List.empty)
        val keys = AccountUnrecognisedAccessKeys(
          account = account,
          vulnerableAccessKey = List(CredentialMetadata(
            username = "testuser",
            accessKeyId = "testAccessKey",
            creationDate = new DateTime(),
            status = CredentialActive
          ))
        )
        unrecognisedUsers.disableAccountAccessKeys(
          accountUnrecognisedKeys = keys,
          iamClients = awsAccounts.map(c => AwsClient(c, account, Region.of("us-east-1"))).toList
        )
        sRemovePassword.get() shouldBe 0
        fRemovePassword.get() shouldBe 0
        sDisableKey.get() shouldBe 0
        fDisableKey.get() shouldBe 1
      }

      "handle disabling a key when the account is present with a success metric" in {
        val awsAccounts = Some(account)
        val (sRemovePassword, fRemovePassword, sDisableKey, fDisableKey, unrecognisedUsers) = getUnrecognisedUsers(dryRun, awsAccounts, janusUsernames = List.empty, allowedAccountIds = List.empty)
        val keys = AccountUnrecognisedAccessKeys(
          account = account,
          vulnerableAccessKey = List(CredentialMetadata(
            username = "testuser",
            accessKeyId = "testAccessKey",
            creationDate = new DateTime(),
            status = CredentialActive
          ))
        )
        val (accessKeyDisabled, iamClient) = getIamAsyncClient
        unrecognisedUsers.disableAccountAccessKeys(
          accountUnrecognisedKeys = keys,
          iamClients = List(AwsClient(iamClient, account, Region.of("us-east-1")))
        ).value()

        accessKeyDisabled.get() shouldBe 1

        sRemovePassword.get() shouldBe 0
        fRemovePassword.get() shouldBe 0
        sDisableKey.get() shouldBe 1
        fDisableKey.get() shouldBe 0
      }
    }
  }

}
