package org.w3.vs.view.model

import org.w3.vs.view._
import play.api.libs.json.{Writes, Json, JsValue}
import play.api.templates.Html
import org.w3.vs.view.Collection.Definition
import org.w3.vs.model.AssertorId
import play.api.i18n.Messages

case class AssertorView(
    id: AssertorId,
    errors: Int,
    warnings: Int,
    collection: Option[Collection[Model]] = None) extends Model {

  val name: String = Messages(s"assertor.${id.id}")

  def withCollection(collection: Collection[Model]): AssertorView =
    copy(collection = Some(collection))

  def isValid = errors + warnings == 0

  def toJson: JsValue =
    Json.toJson(this)(AssertorView.writes)

  def toHtml: Html =
    views.html.model.assertor(this, collection)

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
          "id" -> toJson(assertor.id.toString),
          "name" -> toJson(assertor.name),
          "errors" -> toJson(assertor.errors),
          "warnings" -> toJson(assertor.warnings)
        )
      )
    }
  }

}