package org.w3.vs.view.model

import org.w3.vs.model._
import org.w3.vs.view._
import play.api.libs.json._
import play.api.templates.Html
import org.w3.vs.view.Collection.Definition
import org.w3.vs.store.Formats._
import org.joda.time.{DateTimeZone, DateTime}
import play.api.libs.json.Json._
import play.api.libs.json.JsUndefined
import org.w3.vs.view.Collection.Definition
import play.api.libs.json.JsObject

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

  def toJson: JsValue = {
    val json: JsObject = Json.toJson(data).asInstanceOf[JsObject]
    val id = json \ "_id" \ "$oid"
    // TODO: This must be implemented client side. temporary
    val completedOn = {
      if (!(json \ "completedOn").isInstanceOf[JsUndefined]) {
        val timestamp = new DateTime((json \ "completedOn").as[Long], DateTimeZone.UTC)
        Json.obj(
          "timestamp" -> Json.toJson(timestamp.toString()),
          "legend1" -> Json.toJson(Helper.formatTime(timestamp)),
          "legend2" -> Json.toJson("") /* the legend is hidden for now. Doesn't make sense to compute it here anyway */
        )
      } else {
        Json.obj("legend1" -> Json.toJson("Never"))
      }
    }
    // Replace the _id field with id and replace completedOn by its object
    json -
      "_id" +
      ("id", id) -
      "completedOn" +
      ("completedOn", completedOn)

  }

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
