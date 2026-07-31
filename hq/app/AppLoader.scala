import play.api.ApplicationLoader.Context
import play.api.{Application, ApplicationLoader}

class AppLoader extends ApplicationLoader {
  override def load(context: Context): Application = {
    val components = new AppComponents(context)

    components.application
  }
}
