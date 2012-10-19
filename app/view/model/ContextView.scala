/*package org.w3.vs.view.model

import org.w3.vs.model.Context
import play.api.templates.Html
import org.w3.vs.view._
import play.api.libs.json.JsValue

case class ContextView(
    line: Option[Int],
    column: Option[Int],
    content: Option[Html]) extends View {

  def toJson(): JsValue = ???

}

object ContextView {

  def apply(context: Context): ContextView = {
    ContextView(
      context.line,
      context.column,
      context.content match {
        case "" => None
        case s => Some(Html(s))
      }
    )
  }

} */