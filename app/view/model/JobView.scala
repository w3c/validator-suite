package org.w3.vs.view.model

import org.w3.vs.model.{Job, JobId}
import org.w3.util.{FutureVal, URL}
import org.joda.time.DateTime
import org.w3.vs.view._

case class JobView(
    id: JobId,
    name: String,
    url: URL,
    status: String,
    lastCompleted: Option[DateTime],
    warnings: Int,
    errors: Int,
    resources: Int,
    maxResources: Int,
    health: Int) extends View

object JobView {

  val params = Seq[String](
    "name",
    "url",
    "status",
    "completed",
    "warnings",
    "errors",
    "resources",
    "maxResources",
    "health"
  )

  implicit def ec = org.w3.vs.Prod.configuration.webExecutionContext

  def fromJob(job: Job): FutureVal[Exception, JobView] = {
    for {
      activity <- job.getActivity()
      lastCompleted <- job.getLastCompleted()
      data <- job.getData()
    } yield JobView(
      job.id,
      job.name,
      job.strategy.entrypoint,
      activity.toString,
      lastCompleted,
      data.warnings,
      data.errors,
      data.resources,
      job.strategy.maxResources,
      data.health
    )
  }

  def fromJobs(jobs: Iterable[Job]): FutureVal[Exception, Iterable[JobView]] = {
    FutureVal.sequence(jobs.map(fromJob _))
  }

  val filtering: PageFiltering[JobView] = new PageFiltering[JobView] {

    def filter(param: Option[String]): (JobView) => Boolean = _ => true

    def validate(filter: Option[String]): Option[String] = None

  }

  val ordering: PageOrdering[JobView] = new PageOrdering[JobView] {

    val params: Iterable[String] = Iterable(
      "name",
      "url",
      "status",
      "completed",
      "warnings",
      "errors",
      "resources",
      "maxResources",
      "health")

    val default: SortParam = SortParam("name", ascending = true)

    def order_(safeParam: SortParam): Ordering[JobView] = {
      val ord = safeParam.name match {
        case "name"         => Ordering[String].on[JobView](_.name)
        case "url"          => Ordering[(String, String)].on[JobView](job => (job.url.toString, job.name))
        case "status"       => Ordering[(String, String)].on[JobView](job => (job.status, job.name))
        case "completed"    => Ordering[(Option[DateTime], String)].on[JobView](job => (job.lastCompleted, job.name))
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
