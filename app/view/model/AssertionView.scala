package org.w3.vs.view.model

import org.w3.util.URL
import org.w3.vs.model.{Context => ContextModel, Assertion, AssertionSeverity}
import org.w3.vs.view._
import org.w3.vs.view.collection.Collection
import play.api.libs.json.{JsNull, Writes, Json, JsValue}
import play.api.templates.{HtmlFormat, Html}
import scala.Some

case class AssertionView(
    assertor: String,
    severity: AssertionSeverity,
    title: Html,
    description: Option[Html],
    occurrences: Int,
    contexts: Iterable[AssertionView.Context] = Iterable.empty,
    resources: Iterable[URL] = Iterable.empty) extends View {

  def toJson(colOpt: Option[Collection[View]]): JsValue =
    Json.toJson(this)(AssertionView.writes)

  def toHtml(colOpt: Option[Collection[View]]): Html =
    views.html.models.assertion(this, colOpt)

  def isEmpty: Boolean = resources.isEmpty && ! description.isDefined

  def occurencesLegend: String = ""
}

object AssertionView {

  case class Context(line: Option[Int], column: Option[Int], content: Option[Html])

  def apply(assertion: Assertion): AssertionView = {
    AssertionView(
      assertor = assertion.assertor,
      severity = assertion.severity,
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
