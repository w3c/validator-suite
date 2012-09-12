package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import com.codecommit.antixml._
import scala.io.Source
import scalaz.Validation
import scalaz.Validation._
import play.api.i18n.Messages
import org.w3.vs.view.Helper

/** An Assertor that reads [[http://code.w3.org/unicorn/wiki/Documentation/Run/Response ObservationResponse]]s from [[scala.io.Source]]s
 */
trait UnicornFormatAssertor extends FromSourceAssertor {
  
  def assert(source: Source): Iterable[Assertion] = {
    val response: Elem = XML.fromSource(source)

    val obversationRef: String = response.attrs get "ref" get
    val obversationLang: String = response.attrs get QName(Some("xml"), "lang") get

    // can be passed, failed, undef
    val status: Option[String] = (response \ "status").headOption.map(_.attrs.get("value").get)

    val assertions: Seq[Assertion] =
      for {
        message <- response \ "message"
      } yield {
        val severity = AssertionSeverity(message.attrs get "type" get)
        val title = (message \ "title").headOption map (htmlString _) getOrElse ("-") // TODO Log messages with empty titles. This shouldn't happen.
        /*val id = message.attrs get "id" match {
          case Some(id) if id != "html5" => id
          case _ => title.hashCode.toString
        }*/
        val url = URL(message.attrs get "ref" getOrElse obversationRef)
        val lang: String = message.attrs get "lang" getOrElse obversationLang
        val contexts =
          for {
            context <- message \ "context"
          } yield {
            val content = htmlString(context)
            //val contextRef = context.attrs get "ref" getOrElse eventRef
            val line = context.attrs get "line" map (_.toInt)
            val column = context.attrs get "column" map (_.toInt)
            Context(content, line, column)
          }
        val descriptionOpt = (message \ "description").headOption map (htmlString _)
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
            """<li>
              |  <span>
              |    <a href="%s">%s</a>
              |    <a href="%s">[external link]</a>
              |  </span>
              |</li>""".stripMargin.format(Helper.encode(url), url, url)
          }.mkString("<ul>", " ", "</ul>")}),
          Warning)
      }
      Assertion(URL(obversationRef), name, List.empty, "en-US", Messages("assertor.noissues", Messages("assertor." + name)), severity, description) +: assertions
    } else
      assertions
  }

  private def htmlString(node: Node): String = {
    removeScope(node).children.mkString.trim
  }

  private def removeScope(node: Node): Node = {
    node match {
      case e: Elem => e.copy(scope = Map.empty, children = e.children.map(removeScope))
      case e => e
    }
  }

}
