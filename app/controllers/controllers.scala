package org.w3.vs

import org.w3.util._
import org.w3.vs.Prod.configuration
import org.w3.vs.model._
import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.data.format.Formatter
import play.api.data._
import play.api.data.FormError
import play.api.libs.iteratee._
import play.api.libs.json.JsValue
import play.api.mvc.Results._
import play.api.mvc._
import play.api.mvc.PathBindable
import play.api._
import scalaz._

package object controllers {
  
  implicit def ec = configuration.webExecutionContext
  
  def CloseWebsocket = (Iteratee.ignore[JsValue], Enumerator.eof)
  
  def isAjax(implicit req: Request[_]) = {
    req.headers get ("x-requested-with") match {
      case Some("XMLHttpRequest") => true
      case _ => false
    }
  }
  
  /*
   *  Forms
   */
  def loginForm = Form(
    tuple(
      "email" -> email,
      "password" -> text
    )
  )
  
  // TODO make the form understand maxNumberOfResources
  def jobForm = Form(
    mapping (
      "name" -> text,
      "url" -> of[URL],
      "distance" -> of[Int],
      "linkCheck" -> of[Boolean](booleanFormatter),
      "maxNumberOfResources" -> of[Int]
    )((name, url, distance, linkCheck, maxNumberOfResources) => {
      Job(
        name = name,
        organizationId = null,
        creatorId = null,
        strategy = Strategy(
          entrypoint=url,
          distance=distance,
          linkCheck=linkCheck,
          maxNumberOfResources=maxNumberOfResources,
          filter=Filter(include=Everything, exclude=Nothing)))
    })
    ((job: Job) => Some(job.name, job.strategy.entrypoint, job.strategy.distance, job.strategy.linkCheck, job.strategy.maxNumberOfResources))
  )

  class FormW[T](form: Form[T]) {
    def toValidation: Validation[Form[T], T] =
      form.fold(f => Failure(f), s => Success(s))
  }

  implicit def toFormW[T](form: Form[T]): FormW[T] = new FormW(form)
  
  def isValidForm[E](form: Form[E])(implicit req: Request[_]) = form.bindFromRequest.toValidation
  
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
  
}
