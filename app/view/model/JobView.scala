package org.w3.vs.view.model

import org.joda.time.DateTime
import org.w3.util.URL
import org.w3.vs.model._
import org.w3.vs.view._
import org.w3.vs.view.collection.{ResourcesView, AssertionsView, Collection}
import play.api.libs.json._
import play.api.templates.Html
import scala.concurrent._

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
    health: Int,
    collection: Option[Either[Collection[AssertionView], Collection[ResourceView]]] = None) extends View {

  def toJson: JsValue =
    Json.toJson(this)(JobView.writes)

  def toHtml: Html = {

    /*val colOpt = if (assertionsCol.isDefined)
        assertionsCol.map(Left(_))
      else if (resourcesCol.isDefined)
        resourcesCol.map(Right(_))
      else
        None*/

    views.html.models.job(
      job = this,
      collection = collection
    )
  }

}

object JobView {

  /*val params = Seq[String](
    "name",
    "entrypoint",
    "status",
    "completedOn",
    "warnings",
    "errors",
    "resources",
    "maxResources",
    "health"
  )*/

  def apply(job: Job)(implicit ec: ExecutionContext): Future[JobView] = {
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

  def apply(jobs: Iterable[Job])(implicit ec: ExecutionContext): Future[Iterable[JobView]] = {
    Future.sequence(jobs.map(apply _))
  }

  /*val filtering: PageFiltering[JobView] = new PageFiltering[JobView] {

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
  }*/

  implicit val writes: Writes[JobView] = new Writes[JobView] {
    def writes(job: JobView): JsValue = {
      JsObject(Seq(
        ("id"           -> JsString(job.id.toString)),
        ("name"         -> JsString(job.name)),
        ("entrypoint"   -> JsString(job.entrypoint.toString)),
        ("status"       -> JsString(job.status)),
        ("completedOn"  -> {
          job.completedOn match {
            case Some(d) =>
              JsObject(
                Seq(
                  ("timestamp"    -> JsString(d.toString)),
                  ("legend1"      -> JsString(Helper.formatTime(d))),
                  ("legend2"      -> JsString(Helper.formatLegendTime(d)))))
            case None => JsNull
          }
        }),
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
