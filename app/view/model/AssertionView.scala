package org.w3.vs.view.model

import org.w3.vs.web.URL
import org.joda.time.DateTime
import org.w3.vs.model.{Context => ContextModel, _}
import org.w3.vs.view._
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.templates.{HtmlFormat, Html}
import org.w3.vs.view.Collection.Definition
import org.w3.vs.store.Formats._
import org.w3.vs.view.Collection.Definition

case class AssertionView(
  jobId: JobId,
  data: Assertion) extends Model {

  def assertor = data.assertor
  val contexts = data.contexts.toSeq.sorted(
    Ordering[(Int, Int)].on[ContextModel](context => (context.line.getOrElse(Int.MaxValue), context.column.getOrElse(Int.MaxValue)))
  )
  def description = data.description.map(HtmlFormat.raw)
  def lang = data.lang
  def severity = data.severity
  def timestamp = data.timestamp
  def title = HtmlFormat.raw(data.title)
  def url = data.url
  def occurrences = data.occurrences
  def id = data.id.toString

  def toJson: JsValue =
    Json.toJson(data).asInstanceOf[JsObject] //+
      //("id" -> Json.toJson(id)) // because "id" is not part of the json model of an Assertion, cf Formats

  def toHtml: Html =
    views.html.model.assertion(this)

  def isEmpty: Boolean = contexts.isEmpty && !description.isDefined

  def occurrencesLegend: String = {
    if (occurrences > 1) Messages("assertion.occurrences.count", occurrences)
    else Messages("assertion.occurrences.count.one")
  }
}

object AssertionView {

  case class Context(line: Option[Int], column: Option[Int], content: Option[Html])

  def definitions: Seq[Definition] = Seq(
    ("assertor" -> true),
    ("severity" -> true),
    ("occurrences" -> true),
    ("title" -> true),
    ("description" -> true),
    ("contexts" -> true)
  ).map(a => Definition(a._1, a._2))

//  def apply(jobId: JobId, assertion: Assertion): AssertionView = {
//    AssertionView(
//      jobId = jobId,
//      data = assertion)
//  }

  /*def apply(jobId: JobId, assertion: Assertion): AssertionView = {
    AssertionView(
      id = assertion.title.hashCode,
      jobId = jobId,
      assertor = assertion.assertor,
      severity = assertion.severity,
      validated = assertion.timestamp,
      title = HtmlFormat.raw(assertion.title),
      description = assertion.description.map(HtmlFormat.raw),
      occurrences = scala.math.max(1, assertion.contexts.size),
      resources = Iterable.empty,
      contexts = assertion.contexts.toSeq.sorted(
        Ordering[(Int, Int)].on[ContextModel](context => (context.line.getOrElse(Int.MaxValue), context.column.getOrElse(Int.MaxValue)))
      ).map(context =>
        new Context(
          line = context.line,
          column = context.column,
          content = context.content match {
            case "" => None
            case s => Some(Html(s))
          }
        )
      )
    )
  }

  implicit val writes: Writes[AssertionView] = new Writes[AssertionView] {
    import Json.toJson
    implicit def contextWrites = new Writes[AssertionView.Context] {
      def writes(context: AssertionView.Context): JsValue = {
        toJson(Map(
          "line" -> toJson(context.line),
          "column" -> toJson(context.column),
          "content" -> toJson(context.content)
        ))
      }
    }
    def writes(assertion: AssertionView): JsValue = {
      toJson(Map(
        "id" -> toJson(assertion.id),
        "assertor" -> toJson(assertion.assertor.id.toString),
        "severity" -> toJson(assertion.severity.toString),
        "title" -> toJson(assertion.title.toString),
        "description" -> assertion.description.map(d => toJson(d.toString)).getOrElse(JsNull),
        "occurrences" -> toJson(assertion.occurrences),
        "occurrencesLegend" -> toJson(assertion.occurrencesLegend),
        "contexts" -> toJson(assertion.contexts.take(50)),
        "contextsMore" -> toJson(scala.math.max(0, assertion.contexts.size - 50)),
        "resources" -> toJson(assertion.resources.map(_.toString).take(50)),
        "resourcesMore" -> toJson(scala.math.max(0, assertion.resources.size - 50))
      ))
    }
  }*/

}
