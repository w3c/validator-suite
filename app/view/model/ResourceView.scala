package org.w3.vs.view.model

import org.joda.time.DateTime
import org.w3.util._
import org.w3.vs.model._
import org.w3.vs.view._
import play.api.libs.json._
import play.api.libs.json.JsString
import play.api.libs.json.JsObject
import play.api.libs.json.JsNumber
import scala.Some
import org.w3.vs.view.collection.Collection
import play.api.templates.Html

case class ResourceView(
    jobId: JobId,
    url: URL,
    lastValidated: DateTime,
    warnings: Int,
    errors: Int) extends View {

  def toJson(colOpt: Option[Collection[View]]): JsValue = {
    Json.toJson(this)(ResourceView.writes)
  }

  def toHtml(colOpt: Option[Collection[View]]): Html = {
    ???
  }
}

object ResourceView {

  val params = Seq[String](
    "url",
    "validated",
    "warnings",
    "errors"
  )

  /*val filtering: PageFiltering[ResourceView] = new PageFiltering[ResourceView] {

    def validate(filter: Option[String]): Option[String] = None

    def filter(param: Option[String]): (ResourceView) => Boolean = _ => true

    def search(search: Option[String]): (ResourceView) => Boolean = {
      search match {
        case Some(searchString) => {
          case resource
            if (resource.url.toString.contains(searchString)) => true
          case _ => false
        }
        case None => _ => true
      }
    }
  }

  val ordering: PageOrdering[ResourceView] = new PageOrdering[ResourceView] {

    val orderParams = params

    val default: SortParam = SortParam("errors", ascending = false)

    def order_(safeParam: SortParam): Ordering[ResourceView] = {
      val ord = safeParam.name match {
        case "url"       => Ordering[String].on[ResourceView](_.url.toString)
        case "validated" => Ordering[(DateTime, String)].on[ResourceView](view => (view.lastValidated, view.url.toString))
        case "warnings"  => Ordering[(Int, String)].on[ResourceView](view => (view.warnings, view.url.toString))
        case "errors"    => Ordering[(Int, String)].on[ResourceView](view => (view.errors, view.url.toString))
      }
      if (safeParam.ascending) ord else ord.reverse
    }

  }*/

  val writes: Writes[ResourceView] = new Writes[ResourceView] {
    def writes(resource: ResourceView): JsValue = {
      JsObject(Seq(
        ("resourceUrl"   -> JsString(resource.url.toString)),
        ("lastValidated" -> JsObject(Seq(
          ("timestamp"     -> JsString(resource.lastValidated.toString)),
          ("legend1"       -> JsString(Helper.formatTime(resource.lastValidated))),
          ("legend2"       -> JsString(Helper.formatLegendTime(resource.lastValidated)))))),
        ("warnings"      -> JsNumber(resource.warnings)),
        ("errors"        -> JsNumber(resource.errors))
      ))
    }
  }

}