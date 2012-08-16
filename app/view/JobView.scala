package org.w3.vs.view

import org.w3.vs.model.{Job, JobId}
import org.w3.util.{FutureVal, URL}
import org.joda.time.DateTime

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

  // TODO: make the implicit explicit!!!
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

}
