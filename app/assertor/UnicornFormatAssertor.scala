package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import com.codecommit.antixml._
import scala.io.Source
import scalaz.Validation
import scalaz.Validation._
import play.api.i18n.Messages
import org.w3.vs.view.Helper
import org.apache.commons.lang3.StringEscapeUtils

/** An Assertor that reads [[http://code.w3.org/unicorn/wiki/Documentation/Run/Response ObservationResponse]]s from [[scala.io.Source]]s
 */
trait UnicornFormatAssertor extends FromSourceAssertor {
  
  def assert(source: Source): Iterable[Assertion] = {
    val response: Elem = XML.fromSource(source)

    val obversationRef: String = response.attrs.get("ref").get
    val obversationLang: String = response.attrs get QName(Some("xml"), "lang") getOrElse "en"

    // can be passed, failed, undef
    val status: Option[String] = (response \ "status").headOption.map(_.attrs.get("value").get)

    val assertions: Seq[Assertion] =
      for {
        message <- response \ "message"
      } yield {
        val severity = AssertionSeverity(message.attrs.get("type").get)
        val title = (message \ "title").headOption map (htmlString) getOrElse ("-")
        val url = URL(message.attrs get "ref" getOrElse obversationRef)
        val lang = message.attrs get "lang" getOrElse obversationLang
        val group = message.attrs get "group" getOrElse ""

        import scalaz._
        import scalaz.Scalaz._

        val contexts =
          for {
            context <- message \ "context"
          } yield {
            val contextRef: Option[String] = context.attrs get "ref"
            val content = contextRef match {
              case Some(url) => """<a href="%s" target="_blank" class="external">%s</a> %s""" format (url, Helper.shorten(url, 100), htmlString(context))
              case None => htmlString(context)
            }
            //val content = htmlString(context)

            val line = context.attrs get "line" map (_.toInt)
            val column = context.attrs get "column" map (_.toInt)
            Context(content, line, column)
          }
        val descriptionOpt = (message \ "description").headOption map (htmlString)
        Assertion(url, name, contexts.toList, lang, title, severity, descriptionOpt)
      }
    if (!assertions.exists(_.url.toString == obversationRef) && status != Some("undef")) {
      val linkedResources = assertions.collect{
        case assertion if (assertion.url.toString != obversationRef && assertion.severity == Error) => assertion.url
      }.distinct
      val (description, severity) = linkedResources.isEmpty match {
        case true => (None, Info)
        case false => (Some({
          """<p>%s</p>""".format(Messages("assertor.externalResourcesWarning")) +
          linkedResources.map{ case url =>
            """<li class="url">
              |    <a href="%s" class="report" title="%s">
              |      <span>%s</span>
              |      <span>%s</span>
              |    </a>
              |    <br>
              |    <a href="%s" class="external" target="_blank" title="%s">%s</a>
              |</li>"""
            .stripMargin
            .format(
              Helper.encode(url),
              Messages("report.link"),
              Messages("resource.report.for"),
              Helper.shorten(url, 100),
              url,
              Messages("resource.external.link"),
              Helper.shorten(url, 100)
            )
          }.mkString("<ul>", " ", "</ul>")}),
          Warning)
      }
      Assertion(URL(obversationRef), name, List.empty, "en-US", Messages("assertor.noissues", Messages("assertor." + name)), severity, description) +: assertions
    } else
      assertions
  }

  private def htmlString(node: Node): String = {
    removeScope(node).children.mkString("").trim
  }

  private def removeScope(node: Node): Node = {
    node match {
      case e: Elem => e.copy(scope = Map.empty, children = e.children.map(removeScope))
      case e: Text => e.copy(text = StringEscapeUtils.unescapeXml(e.text))
      case e => e
    }
  }

}
