package org.w3.vs.view.model

import java.net.URL
import org.joda.time.DateTime
import org.w3.vs.model._
import org.w3.vs.view._
import play.api.libs.json._
import play.api.templates.Html
import org.w3.vs.view.Collection.Definition

case class ResourceView(
    jobId: JobId,
    url: URL,
    lastValidated: DateTime,
    warnings: Int,
    errors: Int,
    assertions: Option[Collection[AssertionView]]) extends Model {

  def toJson: JsValue =
    Json.toJson(this)(ResourceView.writes)

  def toHtml: Html =
    views.html.model.resource(this, assertions)

}

object ResourceView {

  def definitions: Seq[Definition] = Seq(
    ("url" -> true),
    ("validated" -> true),
    ("warnings" -> true),
    ("errors" -> true),
    ("actions" -> false)
  ).map(a => Definition(a._1, a._2))

  implicit val writes: Writes[ResourceView] = new Writes[ResourceView] {
    def writes(resource: ResourceView): JsValue = {
      JsObject(Seq(
        ("resourceUrl"   -> JsString(resource.url.toString)),
        ("lastValidated" -> JsObject(Seq(
          ("timestamp"     -> JsString(resource.lastValidated.toString)),
          ("legend1"       -> JsString(Helper.formatTime(resource.lastValidated))),
          ("legend2"       -> JsString(Helper.formatLegendTime(resource.lastValidated)))))),
        ("warnings"      -> JsNumber(resource.warnings)),
        ("errors"        -> JsNumber(resource.errors))
      ))
    }
  }

}