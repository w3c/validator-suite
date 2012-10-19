package org.w3.vs.view.collection

import org.w3.vs.view.model.AssertorView
import org.w3.vs.model.{Error, Warning, Assertion}
import play.api.templates.Html

class AssertorsView(val source: Iterable[AssertorView]) extends CollectionImpl[AssertorView] {

  def id: String = "assertors"

  def definitions: Seq[Definition] = Seq(
    ("name" -> true),
    ("errors" -> true),
    ("warnings" -> true)
  ).map(a => Definition(a._1, a._2))

  def emptyMessage: Html = Html("")

  def filter(filter: Option[String]): (AssertorView => Boolean) = _ => true

  def order(sort: Option[SortParam]): Ordering[AssertorView] =
    Ordering[Int].on[AssertorView](assertor => assertor.errors)

  def search(search: Option[String]): (AssertorView => Boolean) = _ => true

}

object AssertorsView {

  def apply(assertions: Iterable[Assertion]): AssertorsView = {
    val views = assertions.groupBy(_.assertor).map {
      case (assertor, assertions) => {
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
        AssertorView(
          assertor,
          errors,
          warnings
        )
      }
    }
    new AssertorsView(views)
  }

}