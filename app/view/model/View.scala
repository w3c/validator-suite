package org.w3.vs.view.model

import play.api.templates.Html
import play.api.libs.json.{JsValue, JsObject}
import org.w3.vs.view.collection.Collection

trait View {

  def toHtml(colOpt: Option[Collection[View]] = None): Html

  def toJson(colOpt: Option[Collection[View]] = None): JsValue

}