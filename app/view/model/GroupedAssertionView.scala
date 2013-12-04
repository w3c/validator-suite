package org.w3.vs.view.model

import org.w3.vs.model._
import org.w3.vs.view._
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.templates.{HtmlFormat, Html}
import org.w3.vs.model.GroupedAssertionData
import org.w3.vs.view.Collection.Definition
import org.w3.vs.store.Formats._
import org.w3.vs.web.URL

case class GroupedAssertionView(
  jobId: JobId,
  data: GroupedAssertionData) extends Model {

  val assertor: AssertorId = data.assertor
  val lang: String = data.lang
  val occurrences: Int = data.occurrences
  val resources: Seq[(URL, Int)] = data.resources.toSeq.sortBy(-_._2).take(50)
  val resourcesCount = data.resources.size
  val severity: AssertionSeverity = data.severity
  val title: Html = HtmlFormat.raw(data.title)
  val id: String = data.id.toString

  def toJson: JsValue = {
    Json.toJson(this)//.asInstanceOf[JsObject] +
      //("occurrencesLegend" -> Json.toJson(occurrencesLegend))
  }

  def toHtml: Html =
    views.html.model.groupedAssertion(this)

  def isEmpty: Boolean = resources.isEmpty

  def occurrencesLegend: String = {
    //if (resources.size > 1) {
      val occ = if (occurrences > 1) Messages("assertion.occurrences.count", occurrences)
      else Messages("assertion.occurrences.count.one")
      Messages("assertion.occurrences.count.resources", occ, data.resources.size)
    /*} else {
      if (occurrences > 1) Messages("assertion.occurrences.count", occurrences)
      else Messages("assertion.occurrences.count.one")
    }*/
  }
}

object GroupedAssertionView {

  def definitions: Seq[Definition] = Seq(
    ("assertor" -> true),
    ("severity" -> true),
    ("occurrences" -> true),
    ("title" -> true),
    ("resources" -> true)
  ).map(a => Definition(a._1, a._2))

  implicit val resourcesWrite: Writes[Seq[(URL, Int)]] = new Writes[Seq[(URL, Int)]] {
    def writes(o: Seq[(URL, Int)]): JsValue = {
      Json.toJson(o.map{ case (url, count) =>
        Json.obj(
          "url" -> url,
          "c" -> count
        )
      })
    }
  }

  implicit val writes: Writes[GroupedAssertionView] = new Writes[GroupedAssertionView] {
    import Json.toJson
    def writes(assertion: GroupedAssertionView): JsValue = {
      toJson(Map(
        "id" -> toJson(assertion.id),
        "assertor" -> toJson(assertion.assertor.id.toString),
        "severity" -> toJson(assertion.severity.toString),
        "title" -> toJson(assertion.title.toString),
        "occurrences" -> toJson(assertion.occurrences),
        "occurrencesLegend" -> toJson(assertion.occurrencesLegend),
        "resources" -> toJson(assertion.resources)(resourcesWrite),
        "resourcesCount" -> toJson(scala.math.max(0, assertion.data.resources.size))
      ))
    }
  }

}
