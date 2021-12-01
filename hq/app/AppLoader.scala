import config.LoggingConfig
import logging.LogConfig
import play.api.ApplicationLoader.Context
import play.api.{Application, ApplicationLoader, Mode}

import scala.concurrent.Future


class AppLoader extends ApplicationLoader {
  override def load(context: Context): Application = {
    val loggingConfig = LoggingConfig(
      context.initialConfiguration,
      sys.env.getOrElse("LOCAL_LOG_SHIPPING", "false").toBoolean
    )

    LogConfig.initPlayLogging(context)
    LogConfig.initRemoteLogShipping(loggingConfig)
    LogConfig.initLocalLogShipping(loggingConfig)

    val components = new AppComponents(context)

    if (context.environment.mode != Mode.Test) {
      components.quartzScheduler.start()

      components.applicationLifecycle.addStopHook { () =>
        Future.successful(components.quartzScheduler.shutdown())
      }
    }

    components.application
  }
}
