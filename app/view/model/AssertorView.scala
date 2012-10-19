package org.w3.vs.view.model

import org.w3.vs.view.collection.Collection
import play.api.libs.json.{Writes, Json, JsValue}
import play.api.templates.Html

case class AssertorView(
    name: String,
    errors: Int,
    warnings: Int) extends View {

  def toJson(colOpt: Option[Collection[View]]): JsValue =
    Json.toJson(this)(AssertorView.writes)

  def toHtml(colOpt: Option[Collection[View]]): Html =
    views.html.models.assertor(this, colOpt)

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