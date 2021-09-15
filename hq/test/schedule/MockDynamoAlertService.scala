package schedule
import model.{AwsAccount, IamAuditUser}

import java.util.concurrent.atomic.AtomicReference

class MockDynamoAlertService extends DynamoAlertService {
  protected val alerts = new AtomicReference[Seq[IamAuditUser]](List.empty)

  override def scanAlert(): Seq[IamAuditUser] = alerts.get()

  override def getAlert(awsAccount: AwsAccount, username: String): Option[IamAuditUser] =
    alerts.get()
      .find(user =>
        user.awsAccount == awsAccount.name && user.username == username
      )

  override def putAlert(alert: IamAuditUser): Unit =
    alerts.updateAndGet(currentAlerts => currentAlerts :+ alert)
}
