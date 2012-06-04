package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import com.codecommit.antixml._
import scala.io.Source

/** An Assertor that reads [[http://code.w3.org/unicorn/wiki/Documentation/Run/Response ObservationResponse]]s from [[scala.io.Source]]s
 */
trait UnicornFormatAssertor extends FromSourceAssertor {
  
  def assert(source: Source, jobId: JobId, runId: RunId): FutureVal[Exception, Iterable[AssertionClosed]] = FutureVal {
    val response: Elem = XML.fromSource(source)
    val obversationRef: String = response.attrs get "ref" get
    val obversationLang = response.attrs get QName(Some("xml"), "lang") get
    val events =
      for {
        message <- response \ "message"
      } yield {
        val assertionId = AssertionId();
        val severity = AssertionSeverity(message.attrs get "type" get)
        val title = (message \ "title").headOption.map{
          title => title.children.map(removeScope).mkString("").trim 
        }.getOrElse("No Title")
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
            val content = context.children.map(removeScope).mkString("").trim
            //val contextRef = context.attrs get "ref" getOrElse eventRef
            val line = context.attrs get "line" map { s => s.toInt }
            val column = context.attrs get "column" map { s => s.toInt }
            Context(ContextId(), content, line, column, assertionId)
          }
        val descriptionOpt = (message \ "description").headOption map { description =>
          description.children.map(removeScope).mkString("").trim
        }
        val assertion = Assertion(assertionId, jobId, runId, id, url, lang, title, severity, descriptionOpt) // T: not great to generate random id. Comes from the fact that the model only really support FromURLAssertors
        AssertionClosed(assertion, contexts)
      }
    events
  }
  
  private def removeScope(node: Node): Node = {
    node match {
      case e: Elem => e.copy(scope = Map.empty, children = e.children.map(removeScope))
      case e => e
    }
  }

}
