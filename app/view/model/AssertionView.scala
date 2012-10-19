package org.w3.vs.view.model

import play.api.templates.{HtmlFormat, Html}
import org.w3.vs.view._
import org.w3.vs.model.{Context => ContextModel, _}
import org.w3.util.URL
import play.api.libs.json.JsValue
import org.w3.vs.view.collection.Collection

case class AssertionView(
    assertor: String,
    severity: AssertionSeverity,
    title: Html,
    description: Option[Html],
    occurrences: Int,
    contexts: Iterable[AssertionView.Context] = Iterable.empty,
    resources: Iterable[URL] = Iterable.empty) extends View {

  def toJson(colOpt: Option[Collection[View]]): JsValue = ???

  def toHtml(colOpt: Option[Collection[View]]): Html = ???

  def isEmpty: Boolean = resources.isEmpty && ! description.isDefined

  def occurencesLegend: String = ???
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

}
