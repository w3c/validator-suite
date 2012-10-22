package org.w3.vs.view.model

import org.w3.vs.view._
import play.api.libs.json.{Writes, Json, JsValue}
import play.api.templates.Html
import org.w3.vs.view.Collection.Definition

case class AssertorView(
    name: String,
    errors: Int,
    warnings: Int,
    assertions: Option[Collection[AssertionView]] = None) extends Model {

  def toJson: JsValue =
    Json.toJson(this)(AssertorView.writes)

  def toHtml: Html =
    views.html.model.assertor(this, assertions)

}

object AssertorView {

  def definitions: Seq[Definition] = Seq(
    ("name" -> true),
    ("warnings" -> true),
    ("errors" -> true),
    ("actions" -> false)
  ).map(a => Definition(a._1, a._2))

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