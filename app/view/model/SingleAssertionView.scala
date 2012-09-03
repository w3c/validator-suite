package org.w3.vs.view.model

import org.w3.util.URL
import org.w3.vs.model._
import play.api.templates.Html
import org.w3.vs.assertor.Assertor
import org.w3.vs.view.{SortParam, PageOrdering, PageFiltering}

case class SingleAssertionView(
  assertorName: String,
  severity: AssertionSeverity,
  message: Html,
  description: Option[Html],
  occurrences: Int,
  url: URL,
  contexts: Iterable[ContextView]) extends AssertionView

object SingleAssertionView {

  val params = Seq[String](
    "assertor",
    "severity",
    "message",
    "description",
    "occurrences",
    "url",
    "contexts"
  )

  def apply(assertion: Assertion): SingleAssertionView = {
    SingleAssertionView(
      assertorName = Assertor.getKey(assertion.assertorId),
      severity = assertion.severity,
      message = Html(assertion.title),
      description = assertion.description.map(Html.apply _),
      occurrences = scala.math.max(1, assertion.contexts.size),
      url = assertion.url,
      contexts = assertion.contexts.toSeq.sorted(
        Ordering[(Int, Int)].on[Context](context => (context.line.getOrElse(1000000), context.column.getOrElse(1000000)))
      ).map(ContextView.apply _)
    )
  }

  def fromAssertions(assertions: Iterable[Assertion]): Iterable[SingleAssertionView] = {
    assertions map (apply _)
  }

  val filtering: PageFiltering[SingleAssertionView] = new PageFiltering[SingleAssertionView] {

    def validate(filter: Option[String]): Option[String] = None

    def filter(param: Option[String]): (SingleAssertionView) => Boolean = _ => true

    def search(search: Option[String]): (SingleAssertionView) => Boolean = _ => true

  }

  val ordering: PageOrdering[SingleAssertionView] = new PageOrdering[SingleAssertionView] {

    val params = Seq[String](
      "assertor",
      "severity",
      "message",
      "description",
      "occurrences",
      "url",
      "contexts"
    )

    val default: SortParam = SortParam("occurrences", ascending = false)

    def order_(safeParam: SortParam): Ordering[SingleAssertionView] = {
      val ord = safeParam.name match {
        case _ => Ordering[Int].on[SingleAssertionView](_.occurrences)
      }
      if (safeParam.ascending) ord else ord.reverse
    }

  }

}