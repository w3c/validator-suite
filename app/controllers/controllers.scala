package org.w3.vs

import org.w3.util._
import org.w3.vs.Prod.configuration
import org.w3.vs.model._
import play.api.data.format.Formats._
import play.api.data.format.Formatter
import play.api.data.FormError
import play.api.libs.iteratee._
import play.api.libs.json.JsValue
import play.api.mvc._
import play.api.mvc.PathBindable
import java.net.URLDecoder
import java.net.URLEncoder

package object controllers {
  
  implicit val system = configuration.system
  
  def CloseWebsocket = (Iteratee.ignore[JsValue], Enumerator.eof)
  
  def isAjax(implicit req: Request[_]) = {
    req.headers get ("x-requested-with") match {
      case Some("XMLHttpRequest") => true
      case _ => false
    }
  }
  
  /*
   *  Formatters
   */
  implicit val urlFormat = new Formatter[URL] {
    override val format = Some("format.url", Nil)
    def bind(key: String, data: Map[String, String]) = {
      stringFormat.bind(key, data).right.flatMap { s =>
        scala.util.control.Exception.allCatch[URL]
          .either(URL(s))
          .left.map(e => Seq(FormError(key, "error.url", Nil)))
      }
    }
    def unbind(key: String, url: URL) = Map(key -> url.toString)
  }
  
  implicit val booleanFormatter = new Formatter[Boolean] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Boolean] =
      Right(data isDefinedAt key)
    def unbind(key: String, value: Boolean): Map[String, String] =
      if (value) Map(key -> "on") else Map()
  }
  
  /*
   *  Bindables
   */
  implicit val bindableJobId = new PathBindable[JobId] {
    def bind (key: String, value: String): Either[String, JobId] = {
      try {
        Right(JobId(value))
      } catch { case e: Exception =>
        Left("invalid id: " + value)
      }
    }
    def unbind (key: String, value: JobId): String = {
      value.toString
    }
  }
  
  implicit val bindableURL = new PathBindable[URL] {
    def bind (key: String, value: String): Either[String, URL] = {
      try {
        Right(URL(URLDecoder.decode(value, "UTF-8")))
      } catch { case e: Exception =>
        Left("invalid url: " + value)
      }
    }
    def unbind (key: String, value: URL): String = {
      URLEncoder.encode(value.toString, "UTF-8")
    }
  }
  
}
