package org.w3.vs.view.collection

import org.w3.vs.model.{Assertion, Error, Warning}
import org.w3.vs.view.Collection._
import org.w3.vs.view._
import org.w3.vs.view.model.{AssertionView, AssertorView}
import play.api.i18n.Messages
import play.api.templates.Html
import controllers.routes

case class AssertorsView(
    source: Iterable[AssertorView],
    id: String = "assertors",
    classe: String = "tabs",
    params: Parameters = Parameters()) extends CollectionImpl[AssertorView] {

  def route = routes.Jobs.index

  def definitions = AssertorView.definitions

  def defaultSortParam = SortParam("", ascending = false)

  def order(sort: SortParam): Ordering[AssertorView] =
    Ordering[(Int, Int, String)].on[AssertorView](v => (-v.errors, -v.warnings, Messages(v.name)))

  def filter(filter: Option[String]): (AssertorView => Boolean) = _ => true

  def search(search: Option[String]): (AssertorView => Boolean) = _ => true

  def emptyMessage: Html = Html("")

  def jsTemplate: Option[Html] = Some(views.html.template.assertor())

  def firstAssertor: String = {
    if (iterable.size > 0) iterable.maxBy(_.errors).id.toString else ""
  }

  def withAssertions(assertions: Collection[AssertionView]): AssertorsView =
    copy(source = source.map(_.copy(assertions = Some(assertions))))

  def copyWith(params: Parameters) = copy(params = params)

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