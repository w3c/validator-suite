/*package org.w3.vs.view

import org.joda.time.DateTime
import org.w3.vs.model.AssertionSeverity
import org.w3.util._
import org.w3.vs.view.model.{ResourceView, JobView, AssertionView}

case class SortParam(name: String, ascending: Boolean) {val descending = !ascending}

trait PageOrdering[A] {

  def orderParams: Iterable[String]
  def default: SortParam

  protected def order_(safeParam: SortParam): Ordering[A]

  final def order(param: SortParam): Ordering[A] = order_(validate(param))
  final def validate(param: SortParam): SortParam = if (orderParams.exists(_ == param.name)) param else default
} */