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
import play.api.mvc.{ Filter => _, _ }
import scala.concurrent._

import OneTimeJobForm.OneTimeJobType
import play.api.data
import play.api.i18n.Messages
import controllers.Assertors
import scala.collection.immutable.Nil

object OneTimeJobForm {

  type OneTimeJobType = (String, URL, OTOJType, Boolean)

  def bind()(implicit req: Request[AnyContent], context: ExecutionContext): Either[OneTimeJobForm, ValidOneTimeJobForm] = {

    val form: Form[OneTimeJobType] = playForm.bindFromRequest

    val vsform: Either[OneTimeJobForm, ValidOneTimeJobForm] = form.fold(
      f => Left(new OneTimeJobForm(f)),
      s => Right(new ValidOneTimeJobForm(form, s))
    )

    vsform
  }

  def blank: OneTimeJobForm = new OneTimeJobForm(playForm)

  implicit val Otojformater = new Formatter[OTOJType]{
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], OTOJType] = {
      Right(OTOJType.fromOpt(data.get("plan")))
    }
    def unbind(key: String, value: OTOJType): Map[String, String] = {
      Map(key -> value.value)
    }
  }

  private def playForm: Form[OneTimeJobType] = Form(
    tuple(
      "name" -> nonEmptyText,
      "entrypoint" -> of[URL],
      "plan" -> of[OTOJType],
      "terms" -> of[Boolean](checkboxFormatter).verifying("not_accepted", _ == true)
    )
  )

}

case class OneTimeJobForm private[view](
    form: Form[OneTimeJobType]) extends VSForm {

  def apply(s: String) = form(s)

  def errors: Seq[(String, String)] = form.errors.map{case error => ("error", Messages(s"form.${error.key}.${error.message}"))}

  def withError(key: String, message: String) = copy(form = form.withError(key, message))

  def withErrors(errors: Seq[(String, String)]) = copy(form = form.copy(errors = errors.map(e => FormError(e._1, e._2)) ++ form.errors))

}

class ValidOneTimeJobForm private[view](
    form: Form[OneTimeJobType],
    bind: OneTimeJobType) extends OneTimeJobForm(form) with VSForm {

  val (name, entrypoint, plan, terms) = bind

  def createJob(user: User)(implicit conf: ValidatorSuite): Job = {
    val strategy = Strategy(
      entrypoint = org.w3.vs.web.URL(entrypoint),
      linkCheck = false,
      filter = Filter.includePrefix(entrypoint.toString),
      maxResources = plan.maxPages,
      assertorsConfiguration = AssertorsConfiguration.default
    )
    Job.createNewJob(name, strategy, user.id)
  }

}
