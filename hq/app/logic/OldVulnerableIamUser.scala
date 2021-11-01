package logic

import com.amazonaws.services.dynamodbv2.model.{AttributeValue, GetItemRequest, PutItemRequest}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementAsync
import com.amazonaws.services.identitymanagement.model.{DeleteLoginProfileResult, UpdateAccessKeyResult}
import com.gu.anghammarad.models.Notification
import config.Config.iamAlertCadence
import model._
import org.joda.time.{DateTime, Days}
import utils.attempt.{Attempt, FailedAttempt, Failure}

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

//TODO is this AttributeValue taken from the correct library, not a jar file is it?
trait AttributeValues {
  def S(str: String) = new AttributeValue().withS(str)
  def L(list: List[AttributeValue]) = new AttributeValue().withL(list.asJava)
  def N(number: Long) = new AttributeValue().withN(number.toString)
  def N(number: Double) = new AttributeValue().withN(number.toString)
  def B(boolean: Boolean) = new AttributeValue().withBOOL(boolean)
  def M(map: Map[String,  AttributeValue]) = new AttributeValue().withM(map.asJava)
}

//TODO don't worry about the target group logic, add that later. put a TODO somewhere.
object OldVulnerableIamUser extends AttributeValues {
  def getAccountVulnerableUsers(crd: CredentialReportDisplay): List[VulnerableUser] =
    findOldAccessKeys(crd).union(findMissingMfa(crd)).distinct

  // note that each vulnerable user will have its own request in its own tuple and there will be duplicate accounts in the return list
  def accountDynamoRequests(accountVulnerableUser: List[(AwsAccount, List[VulnerableUser])], table: String): List[(AwsAccount, VulnerableUser, GetItemRequest)] = {
    accountVulnerableUser.foldRight[List[(AwsAccount, VulnerableUser, GetItemRequest)]](List.empty) { case ((account, users), acc) =>
      acc ++ users.map(user => (account, user, userDynamoRequest(account, user, table)))
    }
  }

  private def userDynamoRequest(account: AwsAccount, user: VulnerableUser, table: String): GetItemRequest = {
    new GetItemRequest().withTableName(table).withKey(Map("id" -> S(s"${account.id}/${user.username}")).asJava)
  }

  // This is a post effectful computation data restructure, called after dynamo get.
  // if the user is not in dynamo, then we want to continue the programme. It just means they haven't been alerted before.
  // the Map[String, AttributeValue] is going to be per user, so double check this function sig.
  def getIamAuditUsers(keys: List[(AwsAccount, VulnerableUser, Map[String, AttributeValue])]): List[(AwsAccount, VulnerableUser, Option[IamAuditUser])] = {
        keys.map { case (account, user, key) => (account, user, keyToIamAuditUser(key))}
  }

  private def keyToIamAuditUser(key: Map[String, AttributeValue]): Option[IamAuditUser] = {
    try {
      val alerts = key("alerts").getL.asScala.map{ a =>
        val alertMap = a.getM.asScala
        IamAuditAlert(
          IamAuditNotificationType.fromName(alertMap("type").getS),
          new DateTime(alertMap("date").getN.toLong),
          new DateTime(alertMap("disableDeadline").getN.toLong))}.toList

      Some(IamAuditUser(
        key("id").getS,
        key("awsAccount").getS,
        key("username").getS,
        alerts))
    } catch {
      case e: Exception =>
        // This user was not in dynamo. This is the first time they have been alerted about.
        None
    }
  }

  case class VulnerableUserWithDeadline(
    username: String,
    key1: AccessKey = AccessKey(NoKey, None),
    key2: AccessKey = AccessKey(NoKey, None),
    humanUser: Boolean,
    tags: List[Tag],
    disableDeadline: DateTime
  ) extends IAMAlert

  def vulnerableUsersWithDynamoDeadline(accountUser: List[(AwsAccount, VulnerableUser, Option[IamAuditUser])]): List[(AwsAccount, VulnerableUserWithDeadline, Option[IamAuditUser])] = {
    accountUser.map { case (account, vulnerableUser, auditUser) => (account, VulnerableUserWithDeadline(
      vulnerableUser.username,
      vulnerableUser.key1,
      vulnerableUser.key2,
      vulnerableUser.humanUser,
      vulnerableUser.tags,
      defineDeadline(auditUser)
    ), auditUser)}
  }

  // create a deadline if the IamAuditUser is None
  private def defineDeadline(auditUser: Option[IamAuditUser]): DateTime = {
    auditUser.fold(DateTime.now.plusDays(iamAlertCadence))(user => getNearestDeadline(user.alerts))
  }

  // TODO fix this as Adam said. Write appropriate tests. What if CRON doesn't run one day.
  private def getNearestDeadline(alerts: List[IamAuditAlert], today: DateTime = DateTime.now): DateTime = {
    val (nearestDeadline, _) = alerts.foldRight[(DateTime, Int)]((DateTime.now, iamAlertCadence)) {
      case (alert, (acc, startingNumberOfDays)) =>
        val daysBetweenTodayAndDeadline: Int = Days.daysBetween(today.withTimeAtStartOfDay, alert.disableDeadline).getDays
        if (daysBetweenTodayAndDeadline < startingNumberOfDays && daysBetweenTodayAndDeadline >= 0) (alert.disableDeadline, daysBetweenTodayAndDeadline)
        else (acc, startingNumberOfDays)
    }
    nearestDeadline
  }

  sealed trait VulnerableUserOperation
  case class WarningAlert(user: IamAuditUser) extends VulnerableUserOperation
  case class FinalAlert(user: IamAuditUser) extends VulnerableUserOperation
  case class Disable(username: String, accessKeyId: String) extends VulnerableUserOperation

  //TODO hang on, what is this method about again?
  // take the user, check it's deadline, work out which kind of operation should be performed
  def triageCandidates(users: List[(AwsAccount, VulnerableUserWithDeadline, Option[IamAuditUser])]): List[(AwsAccount, VulnerableUserOperation)] = {
    users.map { case (account, user, auditUser) =>
      (account,
        auditUser.fold[VulnerableUserOperation]{
        WarningAlert(IamAuditUser(s"${account.id}/${user.username}", account.name, user.username, List(IamAuditAlert(VulnerableCredential, DateTime.now, user.disableDeadline))))
      } { auditUser =>
        if (isWarningAlert(user.disableDeadline)) WarningAlert(auditUser)
        else if (isFinalAlert(user.disableDeadline)) FinalAlert(auditUser)
        else Disable(user.username, "TODO") //TODO work out how to get access key to disable AND then how to get its Ids (try IamListAccessKeys)
      }
      )
    }
  }

  private def isWarningAlert(deadline: DateTime, today: DateTime = DateTime.now): Boolean = {
    deadline.withTimeAtStartOfDay == today.withTimeAtStartOfDay.plusWeeks(1) ||
      deadline.withTimeAtStartOfDay == today.withTimeAtStartOfDay.plusWeeks(3)
  }
  private def isFinalAlert(deadline: DateTime, today: DateTime = DateTime.now): Boolean = deadline.withTimeAtStartOfDay == today.withTimeAtStartOfDay.plusDays(1)


  def generateNotifications(account: AwsAccount, operation: VulnerableUserOperation): Notification = ???

  // take this from IamNotifications
  def createAuditUsersAgain(users: List[(AwsAccount, List[VulnerableUser])]): List[IamAuditUser] = ???

  def disableAccessKey(account: AwsAccount, username: String, accessKeyId: String, iamClient: AmazonIdentityManagementAsync): Attempt[UpdateAccessKeyResult] = ???
  def removePassword(account: AwsAccount, username: String, iamClient: AmazonIdentityManagementAsync): Attempt[DeleteLoginProfileResult] = ???

  private def findOldAccessKeys(credsReport: CredentialReportDisplay): List[VulnerableUser] = {
    val filteredMachines = credsReport.machineUsers.filter(user => VulnerableAccessKeys.hasOutdatedMachineKey(List(user.key1, user.key2)))
    val filteredHumans = credsReport.humanUsers.filter(user => VulnerableAccessKeys.hasOutdatedHumanKey(List(user.key1, user.key2)))
    (filteredMachines ++ filteredHumans).map(VulnerableUser.fromIamUser).toList
  }

  private def findMissingMfa(credsReport: CredentialReportDisplay): List[VulnerableUser] = {
    val filteredHumans = credsReport.humanUsers.filterNot(_.hasMFA)
    filteredHumans.map(VulnerableUser.fromIamUser).toList
  }

  def putRequest(user: IamAuditUser, table: String): PutItemRequest = {
    val item = Map(
    "id" -> S(user.id),
    "awsAccount" -> S(user.awsAccount),
    "username" -> S(user.username),
    "alerts" -> L(user.alerts.map(alert => M(Map(
      "type" -> S(alert.`type`.name),
      "date" -> N(alert.dateNotificationSent.getMillis),
      "disableDeadline" -> N(alert.disableDeadline.getMillis)
    )))))
    new PutItemRequest().withTableName(table).withItem(item.asJava)
  }
}
