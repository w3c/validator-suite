package org.w3.vs.view

import org.w3.vs.model.{Run, Job, JobId}
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
    health: Int) extends View {

  /*import scalaz.Scalaz
  import scalaz._

  def compare(b: JobView, sortParam: (String, Boolean)): Boolean = {
    val comp = sortParam match {
      case ("name", _) => name.compare(b.name)
      case ("url", _) => url.toString.compare(b.url.toString) match {
        case a if a < -1 => false
        case a if a == 0 => name.compare(b.name)
        case _ => true
      }
      case _ => name.compare(b.name)
    }
    if (sortParam._2) comp else !comp
  }*/

}

object JobView {

  // TODO: make the implicit explicit!!!
  implicit def ec = org.w3.vs.Prod.configuration.webExecutionContext

  val name: String = ""

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

}
