package schedule

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, GetItemRequest, PutItemRequest, PutItemResult}
import play.api.Logging
import utils.attempt.{Attempt, Failure}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class AwsDynamoAlertService(client: AmazonDynamoDB) extends Logging {

  def get(request: GetItemRequest)(implicit ec: ExecutionContext): Attempt[Map[String, AttributeValue]] = {
    try {
      Attempt.Right(client.getItem(request).getItem.asScala.toMap)
    } catch {
      case NonFatal(e) =>
        Attempt.Left(
          Failure(s"unable to get item from dynamoDB table",
            s"I haven't been able to get the item you were looking for from the dynamo table for the vulnerable user job",
            500,
            context = Some(e.getMessage),
            throwable = Some(e)
          )
        )
    }
  }

  def put(request: PutItemRequest)(implicit ec: ExecutionContext): Attempt[PutItemResult] = {
    try {
      Attempt.Right(client.putItem(request))
    } catch {
      case NonFatal(e) =>
        Attempt.Left(
          Failure(s"unable to put item to dynamoDB table",
            s"I haven't been able to put the item into the dynamo table for the vulnerable user job",
            500,
            context = Some(e.getMessage),
            throwable = Some(e)
          )
        )
    }
  }
}

