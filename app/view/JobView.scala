package org.w3.vs.view

import org.w3.vs.model.{Run, Job, JobId}
import org.w3.util.{FutureVal, URL}
import org.joda.time.DateTime
import collection.immutable.{SortedSet, SortedMap, HashMap}

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
      run <- job.getRun()
      lastCompleted <- job.getLastCompleted()
    } yield JobView(job, run, lastCompleted)
  }

  def fromJobs(jobs: Iterable[Job]): FutureVal[Exception, Iterable[JobView]] = {
    FutureVal.sequence(jobs.map(fromJob _))
  }

  def apply(job: Job, run: Run, lastCompleted: Option[DateTime]): JobView =
    JobView(
      job.id,
      job.name,
      job.strategy.entrypoint,
      run.activity.toString,
      lastCompleted,
      run.warnings,
      run.errors,
      run.resources,
      job.strategy.maxResources,
      run.jobData.health
    )

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
