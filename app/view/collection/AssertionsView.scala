package org.w3.vs.view.collection

import org.w3.vs.model.{AssertionSeverity, Assertion}
import org.w3.vs.view.model.{JobView, AssertorView, AssertionView}
import play.api.libs.json.JsArray
import play.api.mvc.Request
import play.api.templates.{HtmlFormat, Html}
import org.joda.time.DateTime

class AssertionsView(val source: Iterable[AssertionView]) extends CollectionImpl[AssertionView] {

  def id: String = "assertions"

  def definitions: Seq[Definition] = Seq(
    ("assertor" -> true),
    ("severity" -> true),
    ("title" -> true),
    ("description" -> true),
    ("occurrences" -> true),
    ("url" -> true),
    ("contexts" -> true),
    ("resources" -> true)
  ).map(a => Definition(a._1, a._2))

  def emptyMessage: Html = Html("")

  def filter(filter: Option[String]): (AssertionView => Boolean) = _ => true

  def order(sort: Option[SortParam]): Ordering[AssertionView] = {
    val a = Ordering[AssertionSeverity].reverse
    val b = Ordering[Int].reverse
    val c = Ordering[String]
    Ordering.Tuple3(a, b, c).on[AssertionView](assertion => (assertion.severity, assertion.occurrences, assertion.title.text))
  }

  def search(search: Option[String]): (AssertionView => Boolean) = {
    search match {
      case Some(searchString) => {
        case assertion if (assertion.title.toString.toLowerCase.contains(searchString.toLowerCase)) => true
        case _ => false
      }
      case None => _ => true
    }
  }
}

object AssertionsView {

  def apply(assertions: Iterable[Assertion]): AssertionsView = {
    new AssertionsView(assertions.map(assertion => AssertionView(assertion)))
  }

  def grouped(assertions: Iterable[Assertion]): AssertionsView = {
    // group by title + assertorId
    val views = assertions.groupBy(e => e.title + e.assertor).map { case (_, assertions) =>
      // /!\ assuming that the severity is the same for all assertions sharing the same title.
      val assertorKey = assertions.head.assertor
      val severity = assertions.head.severity
      val title = HtmlFormat.raw(assertions.head.title)
      val description = None //assertions.head.description.map(HtmlFormat.raw)
      val occurrences = assertions.foldLeft(0)((count, assertion) => count + scala.math.max(1, assertion.contexts.size))
      val resources = assertions.map(_.url).toSeq.sortBy(_.toString)

      AssertionView(
        assertor = assertorKey,
        severity = severity,
        title = title,
        description = description,
        occurrences = occurrences,
        resources = resources
      )
    }
    new AssertionsView(views)
  }

}
