package org.w3.vs.view.model

import org.w3.vs.view.collection.{AssertionsView, Collection}
import play.api.libs.json.{Writes, Json, JsValue}
import play.api.templates.Html

case class AssertorView(
    name: String,
    errors: Int,
    warnings: Int,
    assertions: Collection[AssertionView]) extends View {

  def toJson: JsValue =
    Json.toJson(this)(AssertorView.writes)

  def toHtml: Html =
    views.html.models.assertor(this, assertions)

}

object AssertorView {

  implicit val writes: Writes[AssertorView] = new Writes[AssertorView] {

    import Json.toJson

    def writes(assertor: AssertorView): JsValue = {
      toJson(
        Map(
          "name" -> toJson(assertor.name),
          "errors" -> toJson(assertor.errors),
          "warnings" -> toJson(assertor.warnings)
        )
      )
    }
  }

}