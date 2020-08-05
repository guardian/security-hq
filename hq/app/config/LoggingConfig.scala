package config

import com.amazonaws.regions.Regions
import com.amazonaws.util.EC2MetadataUtils
import model.Stage
import play.api.Configuration

case class LoggingConfig (
  stack: String,
  stage: Stage,
  app: String,
  instanceId: String,
  region: Regions,
  streamName: Option[String],
  stsRole: Option[String],
  localLogShippingEnabled: Boolean
)

object LoggingConfig {
  def apply(configuration: Configuration, localLogShippingEnabled: Boolean): LoggingConfig = LoggingConfig(
    configuration.get[String]("stack"),
    Config.getStage(configuration),
    configuration.getOptional[String]("app").getOrElse("security-hq"),
    Option(EC2MetadataUtils.getInstanceId).getOrElse("unknown"),
    Config.region,
    configuration.getOptional[String]("aws.kinesis.logging.streamName"),
    configuration.getOptional[String]("aws.kinesis.logging.stsRoleToAssume"),
    localLogShippingEnabled
  )
}
