package org.w3.vs.view.collection

import org.w3.vs.model._
import org.w3.vs.view.Collection._
import org.w3.vs.view._
import org.w3.vs.view.model.{AssertionView, AssertorView}
import play.api.i18n.Messages
import play.api.templates.Html
import controllers.routes
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.store.Formats._
import play.api.libs.json.JsValue
import scala.Some
import org.w3.vs.view.Collection.Parameters
import org.w3.vs.view.Collection.SortParam
import play.api.mvc.Call
import org.w3.vs.web.URL

case class AssertorsView(
    source: Iterable[AssertorView],
    route: Call,
    params: Parameters = Parameters()) extends CollectionImpl[AssertorView] {

  //def route = routes.Assertors.index()

  val id = "assertors"
  val classe = "tabs"

  def definitions = AssertorView.definitions

  def defaultSortParam = SortParam("", ascending = false)

  def order(sort: SortParam): Ordering[AssertorView] =
    Ordering[(Int, Int, String)].on[AssertorView](v => (-v.errors, -v.warnings, Messages(v.name)))

  def filter(filter: Option[String]): (AssertorView => Boolean) = _ => true

  def search(search: Option[String]): (AssertorView => Boolean) = _ => true

  def emptyMessage: Html = Html("")

  def jsTemplate: Option[Html] = Some(views.html.template.assertor())

  def firstAssertor: String = {
    if (source.size > 0) source.maxBy(_.errors).id.toString else ""
  }

  def withCollection(collection: Collection[Model]): AssertorsView =
    copy(source = source.map(_.withCollection(collection)))

  def copyWith(params: Parameters) = copy(params = params)

}

object AssertorsView {

  def apply(jobId: JobId, url: URL, assertions: AssertionsView): Future[AssertorsView] = {
    Future.successful(new AssertorsView(
      source = getAssertors(assertions.source),
      route = assertions.route
    ))
  }

  def apply(jobId: JobId, groupedAssertions: GroupedAssertionsView): Future[AssertorsView] = {
    Future.successful(new AssertorsView(
      source = getAssertors(groupedAssertions.source),
      route = groupedAssertions.route
    ))
  }

  // a quick surtype of AssertionView, GroupedAssertionView, or just Assertion
  type A = {
    def assertor: AssertorId
    def severity: AssertionSeverity
    def occurrences: Int
  }

  def getAssertors[AA <: A](assertions: Iterable[A]): Iterable[AssertorView] = {
    assertions.groupBy(_.assertor).map {
      case (assertor, assertions) => {
        val errors = assertions.foldLeft(0) {
          case (count, assertion) =>
            count + (assertion.severity match {
              case Error => assertion.occurrences
              case _ => 0
            })
        }
        val warnings = assertions.foldLeft(0) {
          case (count, assertion) =>
            count + (assertion.severity match {
              case Warning => assertion.occurrences
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
  }

}