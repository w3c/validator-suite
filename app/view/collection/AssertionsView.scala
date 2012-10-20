package org.w3.vs.view.collection

import org.w3.vs.model.{AssertionSeverity, Assertion}
import org.w3.vs.view.model.AssertionView
import play.api.templates.{HtmlFormat, Html}
import Collection._
import org.joda.time.DateTime

case class AssertionsView(
    source: Iterable[AssertionView],
    id: String = "assertions",
    classe: String = "folds",
    params: Parameters = Parameters()) extends CollectionImpl[AssertionView] {

  def copyWith(params: Parameters) = copy(params = params)

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

  def emptyMessage: Html = {
    Html("")
  }

  def filter(filter: Option[String]) =
    filter match {
      case Some(param) => {
        case assertion if (assertion.assertor == param) => true
        case _ => false
      }
      case None => _ => true
    }

  def defaultSortParam = SortParam("", ascending = true)

  def order(sort: SortParam) = {
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

  def template: Option[Html] = {
    Some(views.html.template.assertion())
  }

}

object AssertionsView {

  def apply(assertions: Iterable[Assertion]): AssertionsView = {
    AssertionsView(source = assertions.map(assertion => AssertionView(assertion)))
  }

  def grouped(assertions: Iterable[Assertion]): AssertionsView = {
    // group by title + assertorId

    val now = DateTime.now()

    val views = assertions.groupBy(e => e.title + e.assertor).map { case (_, assertions) =>
      // /!\ assuming that the severity is the same for all assertions sharing the same title.
      val assertorKey = assertions.head.assertor
      val severity = assertions.head.severity
      val title = HtmlFormat.raw(assertions.head.title)
      val description = None //assertions.head.description.map(HtmlFormat.raw)
      val resources = assertions.map(_.url).toSeq.sortBy(_.toString)
      val occurrences = assertions.foldLeft(0)((count, assertion) => count + scala.math.max(1, assertion.contexts.size))

      println(title)
      println(occurrences)


      AssertionView(
        assertor = assertorKey,
        severity = severity,
        validated = now,
        title = title,
        description = description,
        occurrences = occurrences,
        resources = resources
      )
    }
    AssertionsView(source = views)
  }

}
