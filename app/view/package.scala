package org.w3.vs

import org.joda.time.DateTime
import org.w3.vs.view.model._
import play.api.libs.json.{Json, JsNull, JsValue, Writes}
import play.api.templates.{HtmlFormat, Html}
import play.api.data.format.Formatter
import org.w3.vs.web.URL
import play.api.data.format.Formats._
import play.api.data.FormError
import org.w3.vs.model.{JobData, Job}

package object view {

  object OTOJType {
    def fromOpt(o: Option[String]): OTOJType = {
      o match {
        case Some("otoj500") => Otoj500
        case Some("otoj2000") => Otoj2000
        case Some("otoj5000") => Otoj5000
        case _ => Otoj250
      }
    }
    def fromJob(job: Job): OTOJType = {
      job.strategy.maxResources match {
        case n if n <= Otoj250.maxPages => Otoj250
        case n if n <= Otoj500.maxPages => Otoj500
        case n if n <= Otoj2000.maxPages => Otoj2000
        case n if n <= Otoj5000.maxPages => Otoj5000
        case _ => throw new Exception("this job max pages exceed the maximum one-time job value")
      }
    }
  }
  sealed trait OTOJType {
    def value: String
    def maxPages: Int
  }
  case object Otoj250 extends OTOJType {
    val value = "otoj250"
    val maxPages = 250
  }
  case object Otoj500 extends OTOJType {
    val value = "otoj500"
    val maxPages = 500
  }
  case object Otoj2000 extends OTOJType{
    val value = "otoj2000"
    val maxPages = 2000
  }
  case object Otoj5000 extends OTOJType{
    val value = "otoj5000"
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

  implicit val datetimeOrdering: Ordering[DateTime] = org.w3.vs.util.DateTimeOrdering

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

//  implicit def optionWrites[A](implicit wa: Writes[A]) = new Writes[Option[A]] {
//    def writes(o: Option[A]): JsValue = {
//      o match {
//        case Some(a) => wa.writes(a)
//        case None => JsNull
//      }
//    }
//  }

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

}