package org.w3.vs.view.model

import java.net.URL
import org.joda.time.DateTime
import org.w3.vs.model.{Context => ContextModel, Assertion, AssertionSeverity}
import org.w3.vs.view._
import play.api.i18n.Messages
import play.api.libs.json.{JsNull, Writes, Json, JsValue}
import play.api.templates.{HtmlFormat, Html}
import org.w3.vs.view.Collection.Definition

case class AssertionView(
    assertor: String,
    severity: AssertionSeverity,
    validated: DateTime,
    title: Html,
    description: Option[Html],
    occurrences: Int,
    contexts: Iterable[AssertionView.Context] = Iterable.empty,
    resources: Iterable[URL] = Iterable.empty) extends Model {

  def toJson: JsValue =
    Json.toJson(this)(AssertionView.writes)

  def toHtml: Html =
    views.html.model.assertion(this)

  def isEmpty: Boolean = resources.isEmpty && ! description.isDefined

  def occurencesLegend: String = {
    if (resources.size > 1) {
      val occ = if (occurrences > 1) Messages("assertion.occurrences.count", occurrences)
                else Messages("assertion.occurrences.count.one")
      Messages("assertion.occurrences.count.resources", occ, resources.size)
    } else {
      if (occurrences > 1) Messages("assertion.occurrences.count", occurrences)
      else Messages("assertion.occurrences.count.one")
    }
  }
}

object AssertionView {

  case class Context(line: Option[Int], column: Option[Int], content: Option[Html])

  def definitions: Seq[Definition] = Seq(
    ("assertor" -> true),
    ("severity" -> true),
    ("occurrences" -> true),
    ("title" -> true),
    ("description" -> true),
    ("contexts" -> true),
    ("resources" -> true)
  ).map(a => Definition(a._1, a._2))

  def apply(assertion: Assertion): AssertionView = {
    AssertionView(
      assertor = assertion.assertor,
      severity = assertion.severity,
      validated = assertion.timestamp,
      title = HtmlFormat.raw(assertion.title),
      description = assertion.description.map(HtmlFormat.raw),
      occurrences = scala.math.max(1, assertion.contexts.size),
      resources = Iterable(assertion.url),
      contexts = assertion.contexts.toSeq.sorted(
        Ordering[(Int, Int)].on[ContextModel](context => (context.line.getOrElse(1000000), context.column.getOrElse(1000000)))
      ).map(context =>
        new Context(
          line = context.line,
          column = context.column,
          content = context.content match {
            case "" => None
            case s => Some(Html(s))
          }
        )
      )
    )
  }

  implicit val writes: Writes[AssertionView] = new Writes[AssertionView] {
    import Json.toJson
    implicit def contextWrites = new Writes[AssertionView.Context] {
      def writes(context: AssertionView.Context): JsValue = {
        toJson(Map(
          "line" -> toJson(context.line),
          "column" -> toJson(context.column),
          "content" -> toJson(context.content)
        ))
      }
    }
    def writes(assertion: AssertionView): JsValue = {
      toJson(Map(
        "assertor" -> toJson(assertion.assertor),
        "severity" -> toJson(assertion.severity.toString),
        "title" -> toJson(assertion.title.toString),
        "description" -> assertion.description.map(d => toJson(d.toString)).getOrElse(JsNull),
        "occurrences" -> toJson(assertion.occurrences),
        "contexts" -> toJson(assertion.contexts),
        "resources" -> toJson(assertion.resources.map(_.toString))
      ))
    }
  }

}
