package config

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
