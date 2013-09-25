package org.w3.vs.view.form

import org.w3.vs.web.URL
import org.w3.vs.ValidatorSuite
import org.w3.vs.assertor.Assertor
import org.w3.vs.model._
import org.w3.vs.view._
import play.api.data.Forms._
import play.api.data._
import play.api.data.format.Formats._
import play.api.data.format._
import play.api.mvc.{Filter => _, _}
import scala.concurrent._

import JobForm.JobType
import play.api.data
import play.api.i18n.Messages
import controllers.Assertors
import scala.collection.immutable.Nil

object JobForm {

  type JobType = (String, URL, Int, Boolean)

  def bind()(implicit req: Request[AnyContent], context: ExecutionContext): Either[JobForm, ValidJobForm] = {

    val form: Form[JobType] = playForm.bindFromRequest

    val vsform: Either[JobForm, ValidJobForm] = form.fold(
      f => Left(new JobForm(f)),
      s => Right(new ValidJobForm(form, s))
    )

    vsform
  }

  def blank: JobForm = new JobForm(playForm)

  private def playForm: Form[JobType] = Form(
    tuple(
      "name" -> nonEmptyText,
      "entrypoint" -> of[URL],
      "maxPages" -> of[Int],
      "terms" -> of[Boolean](checkboxFormatter).verifying("not_accepted", _ == true)
    )
  )

}

case class JobForm private[view](form: Form[JobType]) extends VSForm {

  def apply(s: String) = form(s)

  def errors: Seq[(String, String)] = form.errors.map {
    case error => ("error", Messages(s"form.${error.key}.${error.message}"))
  }

  def withError(key: String, message: String) = copy(form = form.withError(key, message))

  def withErrors(errors: Seq[(String, String)]) = copy(form = form.copy(errors = errors.map(e => FormError(e._1, e._2)) ++ form.errors))

}

class ValidJobForm private[view](
                                  form: Form[JobType],
                                  bind: JobType) extends JobForm(form) with VSForm {

  val (name, entrypoint, maxPages, terms) = bind

  def createJob(user: User)(implicit conf: ValidatorSuite): Job = {
    val strategy = Strategy(
      entrypoint = org.w3.vs.web.URL(entrypoint),
      linkCheck = false,
      filter = Filter.includePrefix(entrypoint.toString),
      maxResources = maxPages,
      assertorsConfiguration = AssertorsConfiguration.default
    )
    Job(name = name, strategy = strategy, creatorId = Some(user.id))
  }

}
