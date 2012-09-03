package org.w3.vs.view.model

import org.w3.util.URL
import org.w3.vs.model.{Context, Assertion, AssertionSeverity}
import play.api.templates.Html
import org.w3.vs.assertor.Assertor
import org.w3.vs.view.{SortParam, PageOrdering, PageFiltering}

case class GroupedAssertionView(
  assertorName: String,
  severity: AssertionSeverity,
  message: Html,
  description: Option[Html],
  occurrences: Int,
  urls: Iterable[URL]) extends AssertionView

object GroupedAssertionView {

  val params = Seq[String](
    "assertor",
    "severity",
    "message",
    "description",
    "occurrences",
    "urls"
  )

  def fromAssertions(assertions: Iterable[Assertion]): Iterable[GroupedAssertionView] = {
    // group by title + assertorId
    assertions.groupBy(e => e.title + e.assertorId).map { case (_, assertions) =>
      // /!\ assuming that the severity is the same for all messages sharing the same title.
      val assertorKey = Assertor.getKey(assertions.head.assertorId)
      val severity = assertions.head.severity
      val message = Html(assertions.head.title)
      val description = assertions.head.description.map(Html.apply _)
      val occurrences = assertions.size
      val urls = assertions.map(_.url).toSeq.sortBy(_.toString)

      GroupedAssertionView(
        assertorKey,
        severity,
        message,
        description,
        occurrences,
        urls
      )
    }
  }

  val filtering: PageFiltering[GroupedAssertionView] = new PageFiltering[GroupedAssertionView] {

    def validate(filter: Option[String]): Option[String] = None

    def filter(param: Option[String]): (GroupedAssertionView) => Boolean = _ => true

    def search(search: Option[String]): (GroupedAssertionView) => Boolean = _ => true

  }

  val ordering: PageOrdering[GroupedAssertionView] = new PageOrdering[GroupedAssertionView] {

    val params = Seq[String](
      "assertor",
      "severity",
      "message",
      "description",
      "occurrences",
      "urls"
    )

    val default: SortParam = SortParam("occurrences", ascending = false)

    def order_(safeParam: SortParam): Ordering[GroupedAssertionView] = {
      val ord = safeParam.name match {
        case _ => Ordering[Int].on[GroupedAssertionView](_.occurrences)
      }
      if (safeParam.ascending) ord else ord.reverse
    }

  }

}
