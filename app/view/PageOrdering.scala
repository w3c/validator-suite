package org.w3.vs.view

import org.joda.time.DateTime
import org.w3.vs.model.AssertionSeverity
import org.w3.util._
import org.w3.vs.view.model.{ResourceView, JobView, AssertionView}

case class SortParam(name: String, ascending: Boolean) {val descending = !ascending}

trait PageOrdering[A] {

  def params: Iterable[String]
  def default: SortParam

  protected def order_(safeParam: SortParam): Ordering[A]

  final def order(param: SortParam): Ordering[A] = order_(validate(param))
  final def validate(param: SortParam): SortParam = if (params.exists(_ == param.name)) param else default
}

object PageOrdering {

  /*implicit val datetimeOptionOrdering: Ordering[Option[DateTime]] = new Ordering[Option[DateTime]] {

    // TODO check this (tom)
    def compare(x: Option[DateTime], y: Option[DateTime]): Int = (x, y) match {
      case (Some(date1), Some(date2)) => date1.compareTo(date2)
      case (None, Some(_)) => +1
      case (Some(_), None) => -1
      case (None, None) => 0
    }

  }*/







}