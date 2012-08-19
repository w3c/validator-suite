package org.w3.vs.view

import org.joda.time.DateTime
import org.w3.vs.model.AssertionSeverity
import org.w3.util._

trait View {

}

object View {

  implicit val datetimeOptionOrdering: Ordering[Option[DateTime]] = new Ordering[Option[DateTime]] {
    // TODO check this (tom)
    def compare(x: Option[DateTime], y: Option[DateTime]): Int = (x, y) match {
      case (Some(date1), Some(date2)) => date1.compareTo(date2)
      case (None, Some(_)) => +1
      case (Some(_), None) => -1
      case (None, None) => 0
    }
  }

  implicit def jobViewOrdering: PageOrdering[JobView] = new PageOrdering[JobView] {
    val params: Iterable[String] = Iterable(
      "name",
      "url",
      "status",
      "completed",
      "warnings",
      "errors",
      "resources",
      "maxResources",
      "health")
    val default: SortParam = SortParam("name", true)
    def order(param: SortParam): Ordering[JobView] = {
      val ord = validate(param).name match {
        case "name"         => Ordering[String].on[JobView](_.name)
        case "url"          => Ordering[(String, String)].on[JobView](job => (job.url.toString, job.name))
        case "status"       => Ordering[(String, String)].on[JobView](job => (job.status, job.name))
        case "completed"    => Ordering[(Option[DateTime], String)].on[JobView](job => (job.lastCompleted, job.name))
        case "warnings"     => Ordering[(Int, String)].on[JobView](job => (job.warnings, job.name))
        case "errors"       => Ordering[(Int, String)].on[JobView](job => (job.errors, job.name))
        case "resources"    => Ordering[(Int, String)].on[JobView](job => (job.resources, job.name))
        case "maxResources" => Ordering[(Int, String)].on[JobView](job => (job.maxResources, job.name))
        case "health"       => Ordering[(Int, String)].on[JobView](job => (job.health, job.name))
      }
      if (param.ascending) ord else ord.reverse
    }
  }

  implicit def resourceViewOrdering: PageOrdering[ResourceView] = new PageOrdering[ResourceView] {
    val params: Iterable[String] = Iterable(
      "url",
      "validated",
      "warnings",
      "errors")
    val default: SortParam = SortParam("errors", false)
    def order(param: SortParam): Ordering[ResourceView] = {
      val ord = validate(param).name match {
        case "url"       => Ordering[String].on[ResourceView](_.url.toString)
        case "validated" => Ordering[(DateTime, String)].on[ResourceView](view => (view.lastValidated, view.url.toString))
        case "warnings"  => Ordering[(Int, String)].on[ResourceView](view => (view.warnings, view.url.toString))
        case "errors"    => Ordering[(Int, String)].on[ResourceView](view => (view.errors, view.url.toString))
      }
      if (param.ascending) ord else ord.reverse
    }
  }

  implicit def assertionViewOrdering: PageOrdering[AssertionView] = new PageOrdering[AssertionView] {
    val params: Iterable[String] = Iterable("url")
    val default: SortParam = SortParam("url", false)
    def order(param: SortParam): Ordering[AssertionView] = {
      val ord = validate(param).name match {
        // TODO
        //case "validated" => Ordering[(DateTime, String)].on[AssertionView](view => (view.lastValidated, view.url.toString))
        case _           => Ordering[(AssertionSeverity, Int, String)].on[AssertionView](a => (a.severity, a.occurrences, a.message.text))
      }
      if (param.ascending) ord else ord.reverse
    }
  }

}