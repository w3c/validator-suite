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
import org.w3.vs.store.Formats._

case class JobView(
    id: JobId,
    name: String,
    entrypoint: URL,
    status: JobDataStatus,
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
    job.getRunData() map { data =>
      val completedOn: Option[DateTime] = job.latestDone.map(_.completedOn)
      JobView(
        job.id,
        job.name,
        job.strategy.entrypoint,
        data.status,
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
    def writes(job: JobView): JsValue = Json.toJson(
      // This will disappear soon
      new JobData(job.id, job.name, org.w3.util.URL(job.entrypoint), job.status, job.completedOn, job.warnings, job.errors, job.resources, job.maxResources, job.health)
    )
  }

}
