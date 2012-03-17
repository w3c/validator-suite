package org.w3.vs

import play.api.mvc.Request
import play.api.mvc.AnyContent
import play.api.mvc.Result
import play.api.mvc.Results
import play.api.mvc.Action
import play.api.mvc.WebSocket
import play.api.mvc.RequestHeader
import play.api.mvc.AsyncResult
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsValue
import play.api.libs.concurrent.Promise
import play.api.data._
import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.data.FormError
import play.api.data.format.Formatter
import org.w3.util._
import org.w3.vs.Prod.configuration
import org.w3.vs.model._
import play.api.mvc.PathBindable
import play.api.mvc.JavascriptLitteral
import java.util.UUID
import scalaz.Validation
import scalaz.Success
import scalaz.Failure

package object controllers {
  
  implicit def ec = configuration.webExecutionContext
  
  def CloseWebsocket = (Iteratee.ignore[JsValue], Enumerator.eof)
  
  /*
   *  Forms
   */
  def loginForm = Form(
    tuple(
      "email" -> email,
      "password" -> text
    )
  )
  
  def jobForm = Form(
    mapping (
      "name" -> text,
      "url" -> of[URL],
      "distance" -> of[Int],
      "linkCheck" -> of[Boolean](booleanFormatter)
    )((name, url, distance, linkCheck) => {
      Job(
        name = name,
        organization = null,
        creator = null,
        strategy = new EntryPointStrategy(
          name="irrelevantForV1",
          entrypoint=url,
          distance=distance,
          linkCheck=linkCheck,
          filter=Filter(include=Everything, exclude=Nothing)))
    })
    ((job: Job) => Some(job.name, job.strategy.seedURLs.head, job.strategy.distance, job.strategy.linkCheck))
  )

  class FormW[T](form: Form[T]) {
    def toValidation: Validation[Form[T], T] =
      form.fold(f ⇒ Failure(f), s ⇒ Success(s))
  }

  implicit def toFormW[T](form: Form[T]): FormW[T] = new FormW(form)
  
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
  implicit val bindableUUID = new PathBindable[UUID] {
    def bind (key: String, value: String): Either[String, UUID] = {
      try {
        Right(UUID.fromString(value))
      } catch { case e: Exception =>
        Left("invalid id: " + value)
      }
    }
    def unbind (key: String, value: UUID): String = {
      value.toString
    }
  }
  
  implicit val bindableUUIDOption = new PathBindable[Option[UUID]] {
    def bind (key: String, value: String): Either[String, Option[UUID]] = {
      try {
        Right(Some(UUID.fromString(value)))
      } catch { case e: Exception =>
        Left("invalid id: " + value)
      }
    }
    def unbind (key: String, value: Option[UUID]): String = {
      value match {
        case Some(id) => id.toString
        case _ => ""
      }
    }
  }
  
  /*
   *  JavascriptLitteral
   */
  implicit val litteralOptionUUID: JavascriptLitteral[Option[UUID]] = new JavascriptLitteral[Option[UUID]] {
    def to (option: Option[UUID]): String = {
      option match {
        case Some(id) => id.toString
        case _ => ""
      }
    }
  }
  
}
