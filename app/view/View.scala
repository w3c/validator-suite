package org.w3.vs.view

import play.api.libs.json.JsValue
import play.api.templates.Html

trait View {

  def toHtml: Html

  def toJson: JsValue

}