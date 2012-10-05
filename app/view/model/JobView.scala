package org.w3.vs.view.model

import org.w3.vs.model._
import org.w3.util.{FutureVal, URL}
import org.joda.time.DateTime
import org.w3.vs.view._
import akka.dispatch.ExecutionContext
import play.api.libs.json._
import play.api.libs.json.JsString
import play.api.libs.json.JsObject
import play.api.libs.json.JsNumber
import scala.Some
import org.w3.vs.view.SortParam

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
    health: Int) extends View {

  def toJson(): JsValue = {
    Json.toJson(this)(JobView.writes)
  }

}

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
      if (safeParam.ascending) ord else ord.reverse
    }
  }

  import scalaz.Scalaz._

  val writes: Writes[JobView] = new Writes[JobView] {
    def writes(job: JobView): JsValue = {
      JsObject(Seq(
        ("id"           -> JsString(job.id.toString)),
        ("name"         -> JsString(job.name)),
        ("entrypoint"   -> JsString(job.entrypoint.toString)),
        ("status"       -> JsString(job.status)),
        ("completedOn"  -> job.completedOn.fold[JsValue](d => JsObject(Seq(
            ("timestamp"    -> JsString(d.toString)),
            ("legend1"      -> JsString(Helper.formatTime(d))),
            ("legend2"      -> JsString(Helper.formatLegendTime(d))))), JsNull)),
        ("warnings"     -> JsNumber(job.warnings)),
        ("errors"       -> JsNumber(job.errors)),
        ("resources"    -> JsNumber(job.resources)),
        ("maxResources" -> JsNumber(job.maxResources)),
        ("health"       -> JsNumber(job.health))
      ))
    }
  }

  def toJobMessage(jobId: JobId, data: JobData, activity: RunActivity): JsValue = {
    JsObject(Seq(
      ("id"           -> JsString(jobId.toString)),
      ("status"       -> JsString(activity.toString)),
      ("warnings"     -> JsNumber(data.warnings)),
      ("errors"       -> JsNumber(data.errors)),
      ("resources"    -> JsNumber(data.resources)),
      ("health"       -> JsNumber(data.health))
    ))
  }

  def toJobMessage(jobId: JobId, completedOn: DateTime): JsValue = {
    JsObject(Seq(
      ("id"           -> JsString(jobId.toString)),
      ("status"       -> JsString(Idle.toString)),
      ("completedOn"  -> JsObject(Seq(
        ("timestamp"    -> JsString(completedOn.toString)),
        ("legend1"      -> JsString(Helper.formatTime(completedOn))),
        ("legend2"      -> JsString(Helper.formatLegendTime(completedOn))))))
    ))
  }

}
