package logging

import java.net.InetSocketAddress

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Logger => LogbackLogger}
import com.amazonaws.auth.{InstanceProfileCredentialsProvider, STSAssumeRoleSessionCredentialsProvider}
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.gu.logback.appender.kinesis.KinesisAppender
import config.LoggingConfig
import model.DEV
import net.logstash.logback.appender.LogstashTcpSocketAppender
import net.logstash.logback.encoder.LogstashEncoder
import net.logstash.logback.layout.LogstashLayout
import org.slf4j.{LoggerFactory, Logger => SLFLogger}
import play.api.ApplicationLoader.Context
import play.api.LoggerConfigurator
import play.api.libs.json.Json

import scala.util.Try

object LogConfig {
  private val BUFFER_SIZE = 1000
  private val rootLogger: LogbackLogger = LoggerFactory.getLogger(SLFLogger.ROOT_LOGGER_NAME).asInstanceOf[LogbackLogger]

  private def makeCustomFields(config: LoggingConfig): String = {
    Json.toJson(Map(
      "stack" -> config.stack,
      "stage" -> config.stage.toString,
      "app" -> config.app,
      "instanceId" -> config.instanceId
    )).toString()
  }

  private def buildCredentialsProvider(stsRole: String, config: LoggingConfig) = {
    val sessionId = s"${config.app}-session"

    val instanceProvider = InstanceProfileCredentialsProvider.getInstance
    val stsClient = AWSSecurityTokenServiceClientBuilder.standard.withCredentials(instanceProvider).build
    new STSAssumeRoleSessionCredentialsProvider.Builder(stsRole, sessionId).withStsClient(stsClient).build
  }

  def initLocalLogShipping(config: LoggingConfig): Unit = {
    if(config.stage == DEV && config.localLogShippingEnabled) {
      Try {
        rootLogger.info("Initialising local log shipping")
        val customFields = makeCustomFields(config)

        val appender = new LogstashTcpSocketAppender()
        appender.setContext(rootLogger.getLoggerContext)

        // hard code the destination as we're relying on the use of local-elk which only accepts TCP traffic on localhost:5000
        appender.addDestinations(new InetSocketAddress("localhost", 5000))
        appender.setWriteBufferSize(BUFFER_SIZE)

        val encoder = new LogstashEncoder()
        encoder.setCustomFields(customFields)
        appender.setEncoder(encoder)

        encoder.start()
        appender.start()

        rootLogger.addAppender(appender)

        rootLogger.info("Initialised local log shipping")
      } recover {
        case e => rootLogger.error("Failed to initialise local log shipping", e)
      }
    }
  }

  def initRemoteLogShipping(config: LoggingConfig): Unit = {
    if(config.stage == DEV) {
      rootLogger.info("Kinesis logging disabled in DEV")
    } else {
      Try {
        rootLogger.info("Initialising remote log shipping via Kinesis")

        (config.streamName, config.stsRole) match {
          case (Some(streamName), Some(stsRole)) => {
            val customFields = makeCustomFields(config)
            val context = rootLogger.getLoggerContext

            val layout = new LogstashLayout()
            layout.setContext(context)
            layout.setCustomFields(customFields)
            layout.start()

            val appender = new KinesisAppender[ILoggingEvent]()
            appender.setBufferSize(BUFFER_SIZE)
            appender.setRegion(config.region.getName)
            appender.setStreamName(streamName)
            appender.setContext(context)
            appender.setLayout(layout)
            appender.setRoleToAssumeArn(stsRole)
            appender.setCredentialsProvider(buildCredentialsProvider(stsRole, config))

            rootLogger.addAppender(appender)
            rootLogger.info("Initialised remote log shipping")
          }
          case _ => rootLogger.info(s"Missing remote logging configuration streamName=${config.streamName} stsRole=${config.stsRole}")
        }
      } recover {
        case e => rootLogger.error("Failed to initialise remote log shipping", e)
      }
    }
  }

  def initPlayLogging(context: Context): Unit = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment)
    }
  }
}
