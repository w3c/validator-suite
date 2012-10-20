package org.w3.vs.view.model

import play.api.templates.Html
import play.api.libs.json.{JsValue, JsObject}
import org.w3.vs.view.collection.Collection

trait View {

  def toHtml: Html

  def toJson: JsValue

}