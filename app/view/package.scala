package org.w3.vs

import org.joda.time.DateTime
import org.w3.vs.view.model.{ResourceView, JobView, AssertionView}

package object view {

  implicit val datetimeOptionOrdering: Ordering[Option[DateTime]] = new Ordering[Option[DateTime]] {
    // TODO check this (tom)
    def compare(x: Option[DateTime], y: Option[DateTime]): Int = (x, y) match {
      case (Some(date1), Some(date2)) => date1.compareTo(date2)
      case (None, Some(_)) => +1
      case (Some(_), None) => -1
      case (None, None) => 0
    }
  }

  implicit val assertionFiltering = AssertionView.filtering
  implicit val assertionOrdering = AssertionView.ordering

  implicit val jobFiltering = JobView.filtering
  implicit val jobOrdering = JobView.ordering

  implicit val resourceFiltering = ResourceView.filtering
  implicit val resourceOrdering = ResourceView.ordering

}