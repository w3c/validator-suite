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

  implicit def jobViewOrdering(param: (String, Boolean)): Ordering[JobView] = {
    val ordering = param._1 match {
      case "url"          => Ordering[(String, String)].on[JobView](job => (job.url.toString, job.name))
      case "status"       => Ordering[(String, String)].on[JobView](job => (job.status, job.name))
      case "completed"    => Ordering[(Option[DateTime], String)].on[JobView](job => (job.lastCompleted, job.name))
      case "warnings"     => Ordering[(Int, String)].on[JobView](job => (job.warnings, job.name))
      case "errors"       => Ordering[(Int, String)].on[JobView](job => (job.errors, job.name))
      case "resources"    => Ordering[(Int, String)].on[JobView](job => (job.resources, job.name))
      case "maxResources" => Ordering[(Int, String)].on[JobView](job => (job.maxResources, job.name))
      case "health"       => Ordering[(Int, String)].on[JobView](job => (job.health, job.name))
      case _              => Ordering[String].on[JobView](_.name)
    }
    if (param._2) ordering.reverse else ordering
  }

  implicit def resourceViewOrdering(param: (String, Boolean)): Ordering[ResourceView] = {
    val ordering = param._1 match {
      case "url"       => Ordering[String].on[ResourceView](_.url.toString)
      case "validated" => Ordering[(DateTime, String)].on[ResourceView](view => (view.lastValidated, view.url.toString))
      case "warnings"  => Ordering[(Int, String)].on[ResourceView](view => (view.warnings, view.url.toString))
      case _           => Ordering[(Int, String)].on[ResourceView](view => (view.errors, view.url.toString))
    }
    if (param._2) ordering.reverse else ordering
  }

  implicit def assertionViewOrdering(param: (String, Boolean)): scala.Ordering[AssertionView] = {
    val ordering = param._1 match {
      // TODO
      //case "validated" => Ordering[(DateTime, String)].on[AssertionView](view => (view.lastValidated, view.url.toString))
      case _           => Ordering[(AssertionSeverity, Int, String)].on[AssertionView](a => (a.severity, a.occurrences, a.message.text))
    }
    if (param._2) ordering.reverse else ordering
  }

}