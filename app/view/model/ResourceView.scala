package org.w3.vs.view.model

import org.w3.vs.model._
import org.w3.vs.view._
import org.w3.vs.store.Formats._
import play.api.libs.json._
import play.api.templates.Html
import org.w3.vs.view.Collection.Definition
import play.api.libs.json.Json._
import org.w3.vs.view.Collection.Definition
import org.w3.vs.model.ResourceData
import org.joda.time.{DateTimeZone, DateTime}
import play.api.libs.json.JsUndefined
import org.w3.vs.view.Collection.Definition
import play.api.libs.json.JsObject
import org.w3.vs.model.ResourceData

case class ResourceView(
    jobId: JobId,
    data: ResourceData,
    assertions: Option[Collection[AssertionView]] = None) extends Model {

  def url = data.url
  def id = url.hashCode.toString
  def warnings = data.warnings
  def errors = data.errors
  def lastValidated = data.lastValidated

  def toJson: JsValue = {
    val json = Json.toJson(data).asInstanceOf[JsObject]
    // TODO: This must be implemented client side. temporary
    val lastValidated = if (!(json \ "lastValidated").isInstanceOf[JsUndefined]) {
      val timestamp = new DateTime((json \ "lastValidated").as[Long], DateTimeZone.UTC)
      Json.obj(
        "timestamp" -> Json.toJson(timestamp.toString()),
        "legend1" -> Json.toJson(Helper.formatTime(timestamp)),
        "legend2" -> Json.toJson("") /* the legend is hidden for now. Doesn't make sense to compute it here anyway */
      )
    } else {
      Json.obj("legend1" -> Json.toJson("Never"))
    }
    json +
      ("id" -> Json.toJson(id)) -
      "lastValidated" +
      ("lastValidated", lastValidated)
  }


  def toHtml: Html =
    views.html.model.resource(this, assertions)

}

object ResourceView {

  def definitions: Seq[Definition] = Seq(
    ("url" -> true),
    ("lastValidated" -> true),
    ("warnings" -> true),
    ("errors" -> true),
    ("actions" -> false)
  ).map(a => Definition(a._1, a._2))

}