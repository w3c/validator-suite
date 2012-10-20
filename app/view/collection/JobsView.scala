package org.w3.vs.view.collection

import org.w3.vs.view.model.JobView
import play.api.i18n.Messages
import play.api.templates.Html
import org.w3.vs.model.Job
import scala.concurrent.{ExecutionContext, Future}
import org.joda.time.DateTime
import org.w3.vs.view._
import Collection._
import play.api.templates

case class JobsView(
    source: Iterable[JobView],
    id: String = "jobs",
    classe: String = "list",
    params: Parameters = Parameters()) extends CollectionImpl[JobView] {

  def copyWith(params: Parameters) = copy(params = params)

  def definitions: Seq[Definition] = Seq(
    ("name" -> true),
    ("entrypoint" -> true),
    ("status" -> true),
    ("completedOn" -> true),
    ("warnings" -> true),
    ("errors" -> true),
    ("resources" -> true),
    ("maxResources" -> true),
    ("health" -> true),
    ("actions" -> false)
  ).map(a => Definition(a._1, a._2))

  def emptyMessage: Html = {
    import controllers.routes
     templates.Html(s"""${Messages("jobs.empty")} <a href="${routes.Jobs.new1}">${Messages("jobs.create.first")}</a>""")
  }

  def filter(filter: Option[String]): (JobView => Boolean) = _ => true

  def defaultSortParam = SortParam("name", ascending = true)

  def order(sort: SortParam): Ordering[JobView] = {
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
          case "status"       => Ordering[(String, String, String)].on[JobView](job => (job.status, job.name, job.id.toString))
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

  def search(search: Option[String]): (org.w3.vs.view.model.JobView => Boolean) = {
    search match {
      case Some(searchString) => {
        case job if (job.name.toLowerCase.contains(searchString.toLowerCase) || job.entrypoint.toString.toLowerCase.contains(searchString.toLowerCase))
          => true
        case _ => false
      }
      case None => _ => true
    }
  }

  override def bindFromRequest(implicit req: play.api.mvc.Request[_]): JobsView = {
    super.bindFromRequest.asInstanceOf[JobsView]
  }

}

object JobsView {

  def apply(job: Job)(implicit ec: ExecutionContext): Future[JobsView] = {
    JobView(job).map(view => JobsView(source = Iterable(view), classe = "single"))
  }

  def apply(jobs: Iterable[Job])(implicit ec: ExecutionContext): Future[JobsView] = {
    JobView(jobs).map(JobsView(_))
  }

}
