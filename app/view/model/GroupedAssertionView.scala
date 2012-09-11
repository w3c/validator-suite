package org.w3.vs.view.model

import org.w3.util.URL
import org.w3.vs.model.{Context, Assertion, AssertionSeverity}
import play.api.templates.Html
import org.w3.vs.assertor.Assertor
import org.w3.vs.view.{SortParam, PageOrdering, PageFiltering}

case class GroupedAssertionView(
  assertor: String,
  severity: AssertionSeverity,
  title: Html,
  description: Option[Html],
  occurrences: Int,
  resources: Iterable[URL]) extends AssertionView{

  def isEmpty: Boolean = resources.isEmpty && ! description.isDefined
}

object GroupedAssertionView {

  val params = Seq[String](
    "assertor",
    "severity",
    "title",
    "description",
    "occurrences",
    "resources"
  )

  def fromAssertions(assertions: Iterable[Assertion]): Iterable[GroupedAssertionView] = {
    // group by title + assertorId
    assertions.groupBy(e => e.title + e.assertor).map { case (_, assertions) =>
      // /!\ assuming that the severity is the same for all assertions sharing the same title.
      val assertorKey = assertions.head.assertor
      val severity = assertions.head.severity
      val title = Html(assertions.head.title)
      val description = assertions.head.description.map(Html.apply _)
      val occurrences = assertions.size
      val resources = assertions.map(_.url).toSeq.sortBy(_.toString)

      GroupedAssertionView(
        assertorKey,
        severity,
        title,
        description,
        occurrences,
        resources
      )
    }
  }

  val filtering: PageFiltering[GroupedAssertionView] = new PageFiltering[GroupedAssertionView] {

    def validate(filter: Option[String]): Option[String] = filter match {
      case Some(a) if Assertor.names.exists(_ == a)  => Some(a)
      case _ => None
    }

    def filter(param: Option[String]): (GroupedAssertionView) => Boolean = validate(param) match {
      case Some(param) => {
        case assertion if (assertion.assertor == param) => true
        case _ => false
      }
      case None => _ => true
    }

    def search(search: Option[String]): (GroupedAssertionView) => Boolean = {
      search match {
        case Some(searchString) => {
          case assertion
            if (assertion.title.toString.contains(searchString)) => true
          case _ => false
        }
        case None => _ => true
      }
    }

  }

  val ordering: PageOrdering[GroupedAssertionView] = new PageOrdering[GroupedAssertionView] {

    val params = Seq[String](
      "assertor",
      "severity",
      "title",
      "description",
      "occurrences",
      "resources"
    )

    val default: SortParam = SortParam("occurrences", ascending = false)

    def order_(safeParam: SortParam): Ordering[GroupedAssertionView] = {
      //val ord = safeParam.name match {
        //case _ => {
          val a = Ordering[AssertionSeverity].reverse
          val b = Ordering[Int].reverse
          val c = Ordering[String]
          Ordering.Tuple3(a, b, c).on[GroupedAssertionView](assertion => (assertion.severity, assertion.occurrences, assertion.title.text))
        //}
      //}
      //if (safeParam.ascending) ord else ord.reverse
    }

  }

}
