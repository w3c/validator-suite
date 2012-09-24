package org.w3.vs.view.model

import org.w3.vs.model.{Job, JobId}
import org.w3.util.{FutureVal, URL}
import org.joda.time.DateTime
import org.w3.vs.view._
import akka.dispatch.ExecutionContext

case class JobView(
    id: JobId,
    name: String,
    entrypoint: URL,
    status: String,
    completedOn: Option[DateTime],
    warnings: Int,
    errors: Int,
    resources: Int,
    maxResources: Int,
    health: Int) extends View

object JobView {

  val params = Seq[String](
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

  def fromJob(job: Job)(implicit ec: ExecutionContext): FutureVal[Exception, JobView] = {
    for {
      activity <- job.getActivity()
      completedOn <- job.getCompletedOn()
      data <- job.getData()
    } yield JobView(
      job.id,
      job.name,
      job.strategy.entrypoint,
      activity.toString,
      completedOn,
      data.warnings,
      data.errors,
      data.resources,
      job.strategy.maxResources,
      data.health
    )
  }

  def fromJobs(jobs: Iterable[Job])(implicit ec: ExecutionContext): FutureVal[Exception, Iterable[JobView]] = {
    FutureVal.sequence(jobs.map(fromJob _))
  }

  val filtering: PageFiltering[JobView] = new PageFiltering[JobView] {

    def validate(filter: Option[String]): Option[String] = None

    def filter(param: Option[String]): (JobView) => Boolean = _ => true

    def search(search: Option[String]): (JobView) => Boolean = {
      search match {
        case Some(searchString) => {
          case job if (job.name.contains(searchString) || job.entrypoint.toString.contains(searchString))
            => true
          case _
            => false
        }
        case None => _ => true
      }
    }
  }

  val ordering: PageOrdering[JobView] = new PageOrdering[JobView] {

    val orderParams = params

    val default: SortParam = SortParam("name", ascending = true)

    def order_(safeParam: SortParam): Ordering[JobView] = {
      val ord = safeParam.name match {
        case "name"         => Ordering[String].on[JobView](_.name)
        case "entrypoint"   => Ordering[(String, String)].on[JobView](job => (job.entrypoint.toString, job.name))
        case "status"       => Ordering[(String, String)].on[JobView](job => (job.status, job.name))
        case "completedOn"  => Ordering[(Option[DateTime], String)].on[JobView](job => (job.completedOn, job.name))
        case "warnings"     => Ordering[(Int, String)].on[JobView](job => (job.warnings, job.name))
        case "errors"       => Ordering[(Int, String)].on[JobView](job => (job.errors, job.name))
        case "resources"    => Ordering[(Int, String)].on[JobView](job => (job.resources, job.name))
        case "maxResources" => Ordering[(Int, String)].on[JobView](job => (job.maxResources, job.name))
        case "health"       => Ordering[(Int, String)].on[JobView](job => (job.health, job.name))
      }
      if (safeParam.ascending) ord else ord.reverse
    }
  }


}
