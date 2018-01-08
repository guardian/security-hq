import play.api.ApplicationLoader.Context
import play.api.{Application, ApplicationLoader, LoggerConfigurator}


class AppLoader extends ApplicationLoader {
  override def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment)
    }
    val components = new AppComponents(context)
    val app = components.application

    components.startBackgroundServices(app.actorSystem)
    app
  }
}
