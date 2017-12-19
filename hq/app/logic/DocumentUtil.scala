package logic

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.options.MutableDataSet
import java.io.InputStream
import scala.io.Source
import play.twirl.api.Html

object DocumentUtil {

  private val options = new MutableDataSet()
  private val parser = Parser.builder(options).build()
  private val renderer = HtmlRenderer.builder(options).build

  def convert(markdownfile: String): Option[Html] = {
    getClass getResourceAsStream s"/$markdownfile.md" match {
      case is:InputStream => Some(renderInputAsHtml(is))
      case _ => None
    }
  }

  private def renderInputAsHtml(is: InputStream) = {
    val content = Source.fromInputStream(is).mkString
    val document = parser.parse(content)
    new Html(renderer.render(document))
  }

}
