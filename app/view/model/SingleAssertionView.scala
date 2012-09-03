package org.w3.vs.view.model

import org.w3.util.URL
import org.w3.vs.model._
import play.api.templates.Html
import org.w3.vs.assertor.Assertor
import org.w3.vs.view.{SortParam, PageOrdering, PageFiltering}

case class SingleAssertionView(
  assertor: String,
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
      assertor = assertion.assertor,
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

    def validate(filter: Option[String]): Option[String] = filter match {
      case Some(a) if Assertor.names.exists(_ == a)  => Some(a)
      case _ => None
    }

    def filter(param: Option[String]): (SingleAssertionView) => Boolean = validate(param) match {
      case Some(param) => {
        case assertion if (assertion.assertor == param) => true
        case _ => false
      }
      case None => _ => true
    }

    def search(search: Option[String]): (SingleAssertionView) => Boolean = {
      search match {
        case Some(searchString) => {
          case assertion
            if (assertion.message.toString.contains(searchString)) => true
          case _ => false
        }
        case None => _ => true
      }
    }

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
      //val ord = safeParam.name match {
        //case _ => {
          val a = Ordering[AssertionSeverity].reverse
          val b = Ordering[Int].reverse
          val c = Ordering[String]
          Ordering.Tuple3(a, b, c).on[SingleAssertionView](assertion => (assertion.severity, assertion.occurrences, assertion.message.text))
        //}
      //}
      //if (safeParam.ascending) ord else ord.reverse
    }

  }

}