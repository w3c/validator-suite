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
    data: JobData,
    collection: Option[Either[Collection[AssertionView], Collection[ResourceView]]] = None) extends Model {

  def completedOn = data.completedOn
  def entrypoint = data.entrypoint
  def errors = data.errors
  def health = data.health
  def id = data.id
  def maxResources = data.maxResources
  def name = data.name
  def resources = data.resources
  def status = data.status
  def warnings = data.warnings

  def toJson: JsValue = Json.toJson(data)

  def toHtml: Html = views.html.model.job(this, collection)

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

/*  def apply(job: Job)(implicit conf: VSConfiguration): Future[JobView] = {
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
  }*/

}
