package org.w3.vs.view.collection

import org.joda.time.DateTime
import org.w3.vs.model.Job
import org.w3.vs.view.Collection._
import org.w3.vs.view.model.{ResourceView, AssertionView, JobView}
import play.api.i18n.Messages
import play.api.templates.Html
import scala.concurrent.{ExecutionContext, Future}
import org.w3.vs.view.Collection

case class JobsView(
    source: Iterable[JobView],
    id: String = "jobs",
    classe: String = "list",
    params: Parameters = Parameters()) extends CollectionImpl[JobView] {

  def withAssertions(assertions: Collection[AssertionView]): JobsView = {
    copy(source = source.map(_.copy(collection = Some(Left(assertions)))))
  }

  def withResources(resources: Collection[ResourceView]): JobsView = {
    copy(source = source.map(_.copy(collection = Some(Right(resources)))))
  }

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
    Html(s"""${Messages("jobs.empty")} <a href="${routes.Jobs.new1}">${Messages("jobs.create.first")}</a>""")
  }

  def filter(filter: Option[String]): (JobView => Boolean) = _ => true

  def defaultSortParam = SortParam("name", ascending = true)

  def order(sort: SortParam): Ordering[JobView] = {
    implicit val d = org.w3.vs.view.datetimeOrdering
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

  def template: Option[Html] = {
    Some(views.html.template.job())
  }

  override def toHtml: Html = {
    views.html.collection.jobs(this)
  }

}

object JobsView {

  def single(job: Job)(implicit ec: ExecutionContext): Future[JobsView] = {
    JobView(job).map(view => new JobsView(
      source = Iterable(view),
      classe = "single"
    ))
  }

  def apply(jobs: Iterable[Job])(implicit ec: ExecutionContext): Future[JobsView] = {
    JobView(jobs).map(JobsView(_))
  }

}
