import config.LoggingConfig
import logging.LogConfig
import play.api.ApplicationLoader.Context
import play.api.{Application, ApplicationLoader}


class AppLoader extends ApplicationLoader {
  override def load(context: Context): Application = {
    val loggingConfig = LoggingConfig(context.initialConfiguration)

    LogConfig.initPlayLogging(context)
    LogConfig.initRemoteLogShipping(loggingConfig)

    new AppComponents(context).application
  }
}
