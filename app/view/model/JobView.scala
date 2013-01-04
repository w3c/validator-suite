package org.w3.vs.view.model

import java.net.URL
import org.joda.time.DateTime
import org.w3.vs.model._
import org.w3.vs.view._
import play.api.libs.json._
import play.api.templates.Html
import scala.concurrent._
import org.w3.vs.view.Collection.Definition
import org.w3.vs.VSConfiguration

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
    collection: Option[Either[Collection[AssertionView], Collection[ResourceView]]] = None) extends Model {

  def toJson: JsValue =
    Json.toJson(this)(JobView.writes)

  def toHtml: Html =
    views.html.model.job(this, collection)

  def withCollection(collection: Either[Collection[AssertionView], Collection[ResourceView]]): JobView = {
    copy(collection = Some(collection))
  }

}

object JobView {

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

  def apply(job: Job)(implicit conf: VSConfiguration): Future[JobView] = {
    import ExecutionContext.Implicits.global
    import conf._
    for {
      (run, completedOn) <- {
        import akka.dataflow._
        val run: Future[Run] = job.getRun()
        val completedOn: Future[Option[DateTime]] = job.getCompletedOn()
        flow { (run(), completedOn()) }
      }
    } yield {
      val activity = run.activity
      val data = run.jobData
      JobView(
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
  }

  def apply(jobs: Iterable[Job])(implicit conf: VSConfiguration): Future[Iterable[JobView]] = {
    import ExecutionContext.Implicits.global
    Future.sequence(jobs.map(apply _))
  }

  // TODO: use same techniques as in Formats
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
