package org.w3.vs.view.model

import org.w3.vs.model._
import org.w3.vs.view._
import play.api.libs.json._
import play.api.templates.Html
import org.w3.vs.view.Collection.Definition
import org.w3.vs.store.Formats._

case class JobView(
    data: JobData,
    collection: Option[Collection[Model]] = None) extends Model {

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

  def withCollection(collection: Collection[Model]): JobView = {
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

}
