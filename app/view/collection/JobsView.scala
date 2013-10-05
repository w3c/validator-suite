package org.w3.vs.view.collection

import org.joda.time.DateTime
import org.w3.vs.model.{JobDataIdle => Idle, JobDataRunning => Running, JobData, JobDataStatus, Job}
import org.w3.vs.view.Collection._
import org.w3.vs.view.model.{ResourceView, AssertionView, JobView}
import play.api.i18n.Messages
import play.api.templates.Html
import scala.concurrent.{ExecutionContext, Future}
import org.w3.vs.view.{Model, Collection}
import controllers.routes
import org.w3.vs.ValidatorSuite
import org.w3.vs.store.Formats._
import play.api.libs.json.JsValue
import ExecutionContext.Implicits.global

case class JobsView(
    source: Iterable[JobView],
    id: String = "jobs",
    classe: String = "list",
    params: Parameters = Parameters()) extends CollectionImpl[JobView] {

  val route = routes.Jobs.index

  def definitions = JobView.definitions

  def defaultSortParam = SortParam("name", ascending = true)

  def order(sort: SortParam): Ordering[JobView] = {
    implicit val d = org.w3.vs.view.datetimeOrdering

    implicit val statusOrdering = new Ordering[JobDataStatus] {
      def compare(x: JobDataStatus, y: JobDataStatus): Int = {
        (x, y) match {
          case (Idle, Idle) => 0
          case (Idle, _) => 1
          case (Running(p), Running(q)) => {
            (p - q) match {
              case a if a > 0 => 1
              case 0 => 0
              case _ => -1
            }
          }
          case (_, Idle) => -1
        }
      }
    }

    val params = List(
      "name",
      "entrypoint",
      "status",
      "completedOn",
      "warnings",
      "errors",
      "resources",
      "maxResources",
      "health"
    )
    sort match {
      case SortParam(param, ascending) if params.contains(param) => {
        val ord = param match {
          case "name"         => Ordering[(String, String)].on[JobView](job => (job.name, job.id.toString))
          case "entrypoint"   => Ordering[(String, String, String)].on[JobView](job => (job.entrypoint.toString, job.name, job.id.toString))
          case "status"       => Ordering[(JobDataStatus, String, String)].on[JobView](job => (job.status, job.name, job.id.toString))
          case "completedOn"  => Ordering[(Option[DateTime], String, String)].on[JobView](job => (job.completedOn, job.name, job.id.toString))
          case "warnings"     => Ordering[(Int, String, String)].on[JobView](job => (job.warnings, job.name, job.id.toString))
          case "errors"       => Ordering[(Int, String, String)].on[JobView](job => (job.errors, job.name, job.id.toString))
          case "resources"    => Ordering[(Int, String, String)].on[JobView](job => (job.resources, job.name, job.id.toString))
          case "maxResources" => Ordering[(Int, String, String)].on[JobView](job => (job.maxResources, job.name, job.id.toString))
          case "health"       => Ordering[(Int, String, String)].on[JobView](job => (job.health, job.name, job.id.toString))
        }
        if (ascending) ord else ord.reverse
      }
      case _ => order(defaultSortParam)
    }
  }

  def filter(filter: Option[String]): (JobView => Boolean) = _ => true

  def search(search: Option[String]): (JobView => Boolean) = {
    search match {
      case Some(searchString) => {
        case job if (job.name.toLowerCase.contains(searchString.toLowerCase) || job.entrypoint.toString.toLowerCase.contains(searchString.toLowerCase))
          => true
        case _ => false
      }
      case None => _ => true
    }
  }

  def emptyMessage: Html = {
    import controllers.routes
    if (source.size == 0) {
      Html(s"""${Messages("jobs.empty")} <a href="${routes.Jobs.newJob}">${Messages("jobs.create.first")}</a>""")
    } else {
      Html(Messages("search.empty"))
    }
  }

  def jsTemplate: Option[Html] = Some(views.html.template.job())

  def withCollection(collection: Collection[Model]): JobsView =
    copy(source = source.map(_.withCollection(collection)))

//  def withResources(resources: Collection[ResourceView]): JobsView =
//    copy(source = source.map(_.withCollection(Right(resources))))
//
//  def withAssertions(assertions: Collection[AssertionView]): JobsView =
//    copy(source = source.map(_.withCollection(Left(assertions))))

  def copyWith(params: Parameters) = copy(params = params)

  override def toHtml: Html = views.html.collection.jobs(this)

}

object JobsView {

  def apply(job: Job)(implicit conf: ValidatorSuite): Future[JobsView] = {
    //apply(Iterable(job), "single")
    job.getJobData().map(data => JobsView(source = Iterable(JobView(data)), classe = "single"))
  }

  def apply(jobs: Iterable[Job])(implicit conf: ValidatorSuite): Future[JobsView] = {

    val f = Future.sequence(jobs.map(_.getJobData()))
    f.map(datas => JobsView(source = datas.map(data => JobView(data))))
  }

}
