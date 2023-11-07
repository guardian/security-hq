import play.api.ApplicationLoader.Context
import play.api.{Application, ApplicationLoader, Mode}

import scala.concurrent.Future


class AppLoader extends ApplicationLoader {
  override def load(context: Context): Application = {
    val components = new AppComponents(context)

    components.application
  }
}
