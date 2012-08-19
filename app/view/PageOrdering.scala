package org.w3.vs.view

case class SortParam(name: String, ascending: Boolean) {val descending = !ascending}

trait PageOrdering[A] {
  def params: Iterable[String]
  def default: SortParam
  def order(param: SortParam): Ordering[A]
  def validate(param: SortParam): SortParam = {
    if (params.exists(_ == param.name)) param else default
  }
}