package logic

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import java.io.InputStream

import com.vladsch.flexmark.util.data.MutableDataSet

import scala.io.Source
import play.twirl.api.Html

object DocumentUtil {

  private val options = new MutableDataSet()
  private val parser = Parser.builder(options).build()
  private val renderer = HtmlRenderer.builder(options).build

  def convert(markdownfile: String): Option[Html] = {
    getClass getResourceAsStream s"/$markdownfile.md" match {
      case is: InputStream =>
        val sourceString = Source.fromInputStream(is).mkString
        Some(renderInputAsHtml(sourceString))
      case _ =>
        None
    }
  }

  private def renderInputAsHtml(content: String) = {
    val document = parser.parse(content)
    new Html(renderer.render(document))
  }
}
