package org.w3.vs.view.model

import java.net.URL
import org.joda.time.DateTime
import org.w3.vs.model._
import org.w3.vs.view._
import org.w3.vs.store.Formats._
import play.api.libs.json._
import play.api.templates.Html
import org.w3.vs.view.Collection.Definition

case class ResourceView(
    jobId: JobId,
    data: ResourceData,
    assertions: Option[Collection[AssertionView]]) extends Model {

  def url = data.url
  def warnings = data.warnings
  def errors = data.errors
  def lastValidated = data.lastValidated

  def toJson: JsValue =
    Json.toJson(data)

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

}