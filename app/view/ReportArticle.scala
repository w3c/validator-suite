package org.w3.vs.view

import org.w3.vs.model._
import org.w3.util._
import org.joda.time._

sealed trait ReportArticle
case class URLArticle(
    url: URL,
    lastValidated: DateTime,
    warnings: Int,
    errors: Int) extends ReportArticle
    
object URLArticle {
  def apply(t: (URL, DateTime, Int, Int)): URLArticle =
    URLArticle(t._1, t._2, t._3, t._4)
}

case class AssertorArticle(
    id: AssertorId,
    name: String,
    warnings: Int,
    errors: Int) extends ReportArticle

object AssertorArticle {
  def apply(t: (AssertorId, String, Int, Int)): AssertorArticle =
    AssertorArticle(t._1, t._2, t._3, t._4)
}

sealed trait View {

}

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
      run.health
    )
}


case class ResourceView(
    url: URL,
    lastValidated: DateTime,
    warnings: Int,
    errors: Int) extends View

object ResourceView {

  def apply(t: (URL, DateTime, Int, Int)): ResourceView =
    ResourceView(t._1, t._2, t._3, t._4)

  def fromJob(job: Job): FutureVal[Exception, Iterable[ResourceView]] = {
    job.getRun() map {
      run => {
        println(run.urlArticles)
        run.urlArticles map ResourceView.apply _
      }
    }
  }

}

case class AssertionView(
    id: AssertorId,
    name: String,
    warnings: Int,
    errors: Int) extends View






