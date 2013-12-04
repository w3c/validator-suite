package org.w3.vs.view.collection

import org.w3.vs.web.URL
import org.joda.time.DateTime
import org.w3.vs.model._
import org.w3.vs.view._
import org.w3.vs.view.model.{AssertionView, ResourceView}
import play.api.templates.Html
import controllers.routes
import play.api.i18n.Messages
import org.w3.vs.view.Collection.Parameters
import org.w3.vs.view.Collection.SortParam
import scala.concurrent.Future
import org.w3.vs._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc.Call

case class ResourcesView (
    jobId: JobId,
    source: Iterable[ResourceView],
    classe: String,
    route: Call,
    params: Parameters = Parameters()) extends CollectionImpl[ResourceView] {

  val id = "resources"

  def definitions = ResourceView.definitions

  def defaultSortParam = SortParam("errors", ascending = false)

  def order(sort: SortParam): Ordering[ResourceView] = {
    val params = List(
      "url",
      "lastValidated",
      "warnings",
      "errors"
    )
    sort match {
      case SortParam(param, ascending) if params.contains(param) => {
        val ord = param match {
          case "url"           => Ordering[String].on[ResourceView](_.url.toString)
          case "lastValidated" => Ordering[(DateTime, String)].on[ResourceView](view => (view.lastValidated, view.id))
          case "warnings"      => Ordering[(Int, String)].on[ResourceView](view => (view.warnings, view.id))
          case "errors"        => Ordering[(Int, String)].on[ResourceView](view => (view.errors, view.id))
        }
        if (ascending) ord else ord.reverse
      }
      case _ => order(defaultSortParam)
    }
  }

  def filter(filter: Option[String]): (ResourceView => Boolean) = _ => true

  def search(search: Option[String]): (org.w3.vs.view.model.ResourceView => Boolean) = {
    search match {
      case Some(searchString) => {
        case resource if (resource.url.toString.toLowerCase.contains(searchString.toLowerCase)) => true
        case _ => false
      }
      case None => _ => true
    }
  }

  def emptyMessage: Html = Html(Messages("resources.empty"))

  def jsTemplate: Option[Html] = Some(views.html.template.resource())

  def withAssertions(assertions: Collection[AssertionView]): ResourcesView =
    copy(source = source.map(_.copy(assertions = Some(assertions))))

  def copyWith(params: Parameters) = copy(params = params)

}

object ResourcesView {

  def apply(job: Job)(implicit conf: ValidatorSuite): Future[ResourcesView] = {
    for {
      datas <- job.getResourceDatas()
    } yield {
      val views = datas.map(data => ResourceView(job.id, data))
      ResourcesView(
        jobId = job.id,
        source = views,
        classe = "list",
        route = routes.Resources.index(job.id, None)
      )
    }
  }

  def apply(job: Job, url: URL)(implicit conf: ValidatorSuite): Future[ResourcesView] = {
    for {
      data <- job.getResourceData(url)
    } yield {
      val views = Iterable(ResourceView(job.id, data))
      ResourcesView(
        jobId = job.id,
        source = views,
        classe = "single",
        route = routes.Resources.index(job.id, Some(url))
      )
    }
  }

//  def single(url: URL, assertions: Collection[AssertionView], jobId: JobId): ResourcesView = {
//    val last = assertions.source.maxBy(_.validated).validated
//    val errors = assertions.source.foldLeft(0) {
//      case (count, assertion) =>
//        count + (assertion.severity match {
//          case Error => scala.math.max(assertion.contexts.size, 1)
//          case _ => 0
//        })
//    }
//    val warnings = assertions.source.foldLeft(0) {
//      case (count, assertion) =>
//        count + (assertion.severity match {
//          case Warning => scala.math.max(assertion.contexts.size, 1)
//          case _ => 0
//        })
//    }
//    val view = ResourceView(jobId, url, last, warnings, errors, Some(assertions))
//    ResourcesView(
//      jobId = jobId,
//      source = Iterable(view),
//      classe = "single"
//    )
//  }
//
//  def apply(assertions: Iterable[Assertion], jobId: JobId): ResourcesView = {
//    val views = assertions.groupBy(_.url).map {
//      case (url, assertions) => {
//        val last = assertions.maxBy(_.timestamp).timestamp
//        val errors = assertions.foldLeft(0) {
//          case (count, assertion) =>
//            count + (assertion.severity match {
//              case Error => scala.math.max(assertion.contexts.size, 1)
//              case _ => 0
//            })
//        }
//        val warnings = assertions.foldLeft(0) {
//          case (count, assertion) =>
//            count + (assertion.severity match {
//              case Warning => scala.math.max(assertion.contexts.size, 1)
//              case _ => 0
//            })
//        }
//        ResourceView(jobId, url, last, warnings, errors, None)
//      }
//    }
//    ResourcesView(jobId = jobId, source = views)
//  }
}
