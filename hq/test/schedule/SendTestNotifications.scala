package schedule

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.services.sns.{AmazonSNSAsync, AmazonSNSAsyncClientBuilder}
import com.gu.anghammarad.models.{Notification, AwsAccount => Account}
import config.Config
import model._
import org.joda.time.DateTime
import org.scalatest.{DoNotDiscover, FreeSpec, Matchers}
import org.scalatestplus.play.components.OneAppPerSuiteWithComponents
import play.api.routing.Router
import play.api.{BuiltInComponents, BuiltInComponentsFromContext, NoHttpFiltersComponents}
import schedule.IamMessages.VulnerableCredentials.{disabledUsersMessage, disabledUsersSubject}
import schedule.Notifier.notification
import utils.attempt.{Attempt, AttemptValues}

import scala.concurrent.ExecutionContext

/*
  WARNING: This test suite is designed to only be run locally in order to trigger real email notifications to the
  anghammarad.test.alerts Google group. This can be useful for viewing changes to the email format, for example.

  In order to utilise this you can remove the 'DoNotDiscover` annotation on the class and you may
  choose to replace 'ignore' with 'in' in the test cases below, as appropriate, and then run the tests as usual.
 */
@DoNotDiscover
class SendTestNotifications extends FreeSpec with OneAppPerSuiteWithComponents with Matchers with AttemptValues {

  override def components: BuiltInComponents = new BuiltInComponentsFromContext(context) with NoHttpFiltersComponents {
      lazy val router: Router = Router.empty
  }

  sealed trait NotificationType
  object Warning extends NotificationType
  object Final extends NotificationType
  object Disabled extends NotificationType

  private val anghammaradTopicArn = Config.getAnghammaradSNSTopicArn(app.configuration)

  // We provide a REAL snsClient in order to trigger emails to the test inbox
  val securityCredentialsProvider =
    new AWSCredentialsProviderChain(new ProfileCredentialsProvider("security"), DefaultAWSCredentialsProviderChain.getInstance())
  private val securitySnsClient = AmazonSNSAsyncClientBuilder.standard()
    .withCredentials(securityCredentialsProvider)
    .withRegion(Config.region)
    .withClientConfiguration(new ClientConfiguration().withMaxConnections(10))
    .build()

  private def sendTestNotification(snsClient: AmazonSNSAsync, topicArn: Option[String], notificationType: NotificationType)(implicit ec: ExecutionContext): Attempt[String] = {
    val account = AwsAccount("", "Test", "", "123456")
    val users: Seq[VulnerableUser] = Seq(
      VulnerableUser(
        username = "test-user 1",
        key1 = AccessKey(AccessKeyEnabled, Some(DateTime.now.minusMonths(4))),
        key2 = AccessKey(AccessKeyDisabled, Some(DateTime.now.minusMonths(4))),
        humanUser = true,
        tags = List.empty
      ),
      VulnerableUser(
        username = "test-user 2",
        key1 = AccessKey(NoKey, None),
        key2 = AccessKey(AccessKeyDisabled, Some(DateTime.now.minusMonths(5))),
        humanUser = true,
        tags = List.empty
      )
    )
    Notifier.send(sendCorrectNotification(notificationType, users, account), topicArn, snsClient, testMode = true)
  }

  private def sendCorrectNotification(notificationType: NotificationType, users: Seq[VulnerableUser], account: AwsAccount): Notification = notificationType match {
    case Warning => IamNotifications.createVulnerableCredentialsNotification(warning = true, users, account, List.empty)
    case Final => IamNotifications.createVulnerableCredentialsNotification(warning = false, users, account, List.empty)
    case Disabled => notification(disabledUsersSubject(account), disabledUsersMessage(users), List(Account(account.accountNumber)))
  }

  "Trigger real email notification to 'anghammarad.test.alerts' Google group " - {
    import scala.concurrent.ExecutionContext.Implicits.global

    "of type Warning" ignore {
      sendTestNotification(securitySnsClient, anghammaradTopicArn, Warning).isFailedAttempt shouldBe false
    }

    "of type Final" ignore {
      sendTestNotification(securitySnsClient, anghammaradTopicArn, Final).isFailedAttempt shouldBe false
    }

    "of type Disabled" ignore {
      sendTestNotification(securitySnsClient, anghammaradTopicArn, Disabled).isFailedAttempt shouldBe false
    }
  }
}
