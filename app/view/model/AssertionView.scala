package org.w3.vs.view.model

import org.joda.time.DateTime
import play.api.templates.Html
import org.w3.util._
import org.w3.vs.model._
import org.w3.vs.assertor.Assertor
import org.w3.vs.view._

case class AssertionView(
    url: URL,
    assertorName: String,
    severity: AssertionSeverity,
    message: Html,
    description: Option[Html],
    validated: DateTime,
    occurrences: Int,
    contexts: Iterable[ContextView]) extends View

object AssertionView {

  val params = Seq[String](
    "assertor",
    "severity",
    "occurrences",
    "message",
    "description",
    "validated",
    "context"
  )

  def apply(assertion: Assertion): AssertionView = {
    AssertionView(
      assertion.url,
      Assertor.getKey(assertion.assertorId),
      assertion.severity,
      Html(assertion.title),
      assertion.description.map(Html.apply _),
      assertion.timestamp,
      scala.math.max(1, assertion.contexts.size),
      assertion.contexts.toSeq.sorted(
        Ordering[(Int, Int)].on[Context](context => (context.line.getOrElse(1000000), context.column.getOrElse(1000000)))
      ).map(ContextView.apply _)
    )
  }

  def fromAssertions(assertions: Iterable[Assertion]): Iterable[AssertionView] = {
    assertions map (apply _)
  }

  val filtering: PageFiltering[AssertionView] = new PageFiltering[AssertionView] {

    def filter(param: Option[String]): (AssertionView) => Boolean = validate(param) match {
      case Some(param) => {
        case assertion if (assertion.assertorName == param) => true
        case _ => false
      }
      case None => _ => true
    }

    def validate(filter: Option[String]): Option[String] = filter match {
      case Some(a) if Assertor.keys.exists(_ == a)  => Some(a)
      case _ => None
    }

  }

  val ordering: PageOrdering[AssertionView] = new PageOrdering[AssertionView] {

    val params: Iterable[String] = Iterable("")

    val default: SortParam = SortParam("", ascending = true)

    def order_(safeParam: SortParam): Ordering[AssertionView] = {
      val ord = safeParam.name match {
        case _ => {
          val a = Ordering[AssertionSeverity].reverse
          val b = Ordering[Int].reverse
          val c = Ordering[String]
          Ordering.Tuple3(a, b, c).on[AssertionView](assertion => (assertion.severity, assertion.occurrences, assertion.message.text))
        }
      }
      if (safeParam.ascending) ord else ord.reverse
    }

  }



}

