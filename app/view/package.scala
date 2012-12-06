package org.w3.vs

import org.joda.time.DateTime
import org.w3.vs.view.model._
import play.api.libs.json.{Json, JsNull, JsValue, Writes}
import play.api.templates.Html
import scala.Some
import play.api.data.format.Formatter
import play.api.data.FormError
import java.net.URL
import play.api.data.format.Formats._
import play.api.data.FormError
import scala.Some

package object view {

  object OTOJType {
    def fromOpt(o: Option[String]): OTOJType = {
      o match {
        case Some("otoj2") => Otoj2
        case Some("otoj3") => Otoj3
        case _ => Otoj1
      }
    }
  }
  sealed trait OTOJType {
    def index: Int
    def value: String
    def maxPages: Int
  }
  case object Otoj1 extends OTOJType {
    val index = 1
    val value = "otoj1"
    val maxPages = 500
  }
  case object Otoj2 extends OTOJType{
    val index = 2
    val value = "otoj2"
    val maxPages = 2000
  }
  case object Otoj3 extends OTOJType{
    val index = 3
    val value = "otoj3"
    val maxPages = 5000
  }
  implicit val Otojformater = new Formatter[OTOJType]{
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], OTOJType] = {
      Right(OTOJType.fromOpt(data.get("otoj")))
    }
    def unbind(key: String, value: OTOJType): Map[String, String] = {
      Map(key -> value.value)
    }
  }


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

  implicit val booleanFormatter = new Formatter[Boolean] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Boolean] =
      Right(data isDefinedAt key)
    def unbind(key: String, value: Boolean): Map[String, String] =
      if (value) Map(key -> "on") else Map()
  }

  implicit val urlFormat = new Formatter[URL] {
    override val format = Some("format.url", Nil)
    def bind(key: String, data: Map[String, String]) = {
      stringFormat.bind(key, data).right.flatMap { s =>
        scala.util.control.Exception.allCatch[URL]
          .either(new URL(s))
          .left.map(e => Seq(FormError(key, "error.url", Nil)))
      }
    }
    def unbind(key: String, url: URL) = Map(key -> url.toString)
  }

  implicit val jobToJson: Writes[JobView] = JobView.writes

}