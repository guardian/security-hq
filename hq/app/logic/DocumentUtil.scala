package logic

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.options.MutableDataSet
import java.io.InputStream
import play.twirl.api.Html

object DocumentUtil {

  def convert(markdownfile: String): Option[Html] = {
    val filename = s"/$markdownfile.md"
    var is = getClass.getResourceAsStream(filename)
    if (is == null) None else convertInputStream(is)
  }

  private def convertInputStream(is: InputStream) = {
    val options = new MutableDataSet()
    val parser = Parser.builder(options).build()
    val renderer = HtmlRenderer.builder(options).build
    val content = scala.io.Source.fromInputStream(is).mkString
    val document = parser.parse(content)
    Some(new Html(renderer.render(document)))
  }

}
