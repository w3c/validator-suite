package org.w3.vs

import org.joda.time.DateTime
import org.w3.vs.view.model._
import play.api.libs.json.{Json, JsNull, JsValue, Writes}
import play.api.templates.Html
import scala.Some

package object view {

  type Crumb = (String, String)
  type Crumbs = Seq[(String, String)]

  implicit val datetimeOrdering: Ordering[DateTime] = org.w3.util.DateTimeOrdering

  implicit val datetimeOptionOrdering: Ordering[Option[DateTime]] = new Ordering[Option[DateTime]] {
    def compare(x: Option[DateTime], y: Option[DateTime]): Int = (x, y) match {
      case (Some(date1), Some(date2)) => date1.compareTo(date2)
      case (None, Some(_)) => -1
      case (Some(_), None) => +1
      case (None, None) => 0
    }
  }

  implicit val htmlWrites = new Writes[Html] {
    def writes(html: Html): JsValue = {
      Json.toJson(html.toString())
    }
  }

  implicit def optionWrites[A](implicit wa: Writes[A]) = new Writes[Option[A]] {
    def writes(o: Option[A]): JsValue = {
      o match {
        case Some(a) => wa.writes(a)
        case None => JsNull
      }
    }
  }

  implicit val jobToJson: Writes[JobView] = JobView.writes

}