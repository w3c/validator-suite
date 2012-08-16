package org.w3.vs.view

import org.w3.vs.model.Context
import play.api.templates.Html

case class ContextView(
    line: Option[Int],
    column: Option[Int],
    content: Option[Html]) extends View

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

}