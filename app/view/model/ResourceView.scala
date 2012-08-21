package org.w3.vs.view.model

import org.joda.time.DateTime
import org.w3.util._
import org.w3.vs.model._
import org.w3.vs.view._

case class ResourceView(
    url: URL,
    lastValidated: DateTime,
    warnings: Int,
    errors: Int) extends View

object ResourceView {

  val params = Seq[String](
    "url",
    "validated",
    "warnings",
    "errors"
  )

  def fromAssertions(assertions: Iterable[Assertion]): Iterable[ResourceView] = {
    /*assertions.groupBy(_.url) map { case (url, as) =>
      val last = as.maxBy(_.timestamp).timestamp
      var errors = 0
      var warnings = 0
      as foreach { a =>
        a.severity match {
          case Error => errors += math.max(1, a.contexts.size)
          case Warning => warnings += math.max(1, a.contexts.size)
          case Info => ()
        }
      }
      ResourceView(url, last, warnings, errors)
    }*/
    assertions.groupBy(_.url).map {
      case (url, assertions) => {
        val last = assertions.maxBy(_.timestamp).timestamp
        val errors = assertions.foldLeft(0) {
          case (count, assertion) =>
            count + (assertion.severity match {
              case Error => scala.math.max(assertion.contexts.size, 1)
              case _ => 0
            })
        }
        val warnings = assertions.foldLeft(0) {
          case (count, assertion) =>
            count + (assertion.severity match {
              case Warning => scala.math.max(assertion.contexts.size, 1)
              case _ => 0
            })
        }
        ResourceView(url, last, warnings, errors)
      }
    }
  }

  val filtering: PageFiltering[ResourceView] = new PageFiltering[ResourceView] {

    def filter(param: Option[String]): (ResourceView) => Boolean = _ => true

    def validate(filter: Option[String]): Option[String] = None

  }

  val ordering: PageOrdering[ResourceView] = new PageOrdering[ResourceView] {

    val params: Iterable[String] = Iterable(
      "url",
      "validated",
      "warnings",
      "errors")

    val default: SortParam = SortParam("errors", ascending = false)

    def order_(safeParam: SortParam): Ordering[ResourceView] = {
      val ord = safeParam.name match {
        case "url"       => Ordering[String].on[ResourceView](_.url.toString)
        case "validated" => Ordering[(DateTime, String)].on[ResourceView](view => (view.lastValidated, view.url.toString))
        case "warnings"  => Ordering[(Int, String)].on[ResourceView](view => (view.warnings, view.url.toString))
        case "errors"    => Ordering[(Int, String)].on[ResourceView](view => (view.errors, view.url.toString))
      }
      if (safeParam.ascending) ord else ord.reverse
    }

  }

}