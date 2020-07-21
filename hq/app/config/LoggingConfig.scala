package config

import java.net.InetSocketAddress

import com.amazonaws.regions.Regions
import com.amazonaws.util.EC2MetadataUtils
import model.{DEV, Stage}
import play.api.Configuration

case class LoggingConfig (
  stack: String,
  stage: Stage,
  app: String,
  instanceId: String,
  region: Regions,
  streamName: Option[String],
  stsRole: Option[String]
) {
  val isDev: Boolean = stage == DEV
  val localLogShippingEnabled: Boolean = sys.env.getOrElse("LOCAL_LOG_SHIPPING", "false").toBoolean
  lazy val localLogShippingDestination: InetSocketAddress = new InetSocketAddress("localhost", 5000)
}

object LoggingConfig {
  def apply(configuration: Configuration): LoggingConfig = {
    val stack: String = configuration.get[String]("stack")
    val app: String = configuration.getOptional[String]("app").getOrElse("security-hq")
    val instanceId: String = Option(EC2MetadataUtils.getInstanceId).getOrElse("unknown")
    val loggingStreamName: Option[String] = configuration.getOptional[String]("aws.kinesis.logging.streamName")
    val loggingRole: Option[String] = configuration.getOptional[String]("aws.kinesis.logging.stsRoleToAssume")

    LoggingConfig(
      stack,
      Config.getStage(configuration),
      app,
      instanceId,
      Config.region,
      loggingStreamName,
      loggingRole
    )
  }
}
