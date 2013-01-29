package org.w3.vs.view.form

import java.net.URL
import org.w3.vs.VSConfiguration
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

object OneTimeJobForm {

  type OneTimeJobType = (String, URL, OTOJType, Boolean)

  def assertors()(implicit req: Request[AnyContent]): Seq[Assertor] = try {
    req.body.asFormUrlEncoded.get.get("assertor[]").get.map(Assertor.get)
  } catch { case _: Exception =>
    Seq.empty
  }

  def assertorParameters()(implicit req: Request[AnyContent]): AssertorsConfiguration = {
    assertors().map { assertor =>
      val k = assertor.id
      val v: Map[String, List[String]] = req.body.asFormUrlEncoded match {
        case Some(foo) => foo.collect {
          case (param, values) if (param.startsWith(assertor.id + "-")) =>
            (param.replaceFirst("^" + assertor.id + "-", ""), values.toList)
        }.toMap
        case None => Map.empty
      }
      (k -> v)
    }.toMap
  }

  def bind()(implicit req: Request[AnyContent], context: ExecutionContext): Either[OneTimeJobForm, ValidOneTimeJobForm] = {

    val form: Form[OneTimeJobType] = playForm.bindFromRequest

    val vsform: Either[OneTimeJobForm, ValidOneTimeJobForm] = form.fold(
      f => Left(new OneTimeJobForm(f, assertorParameters())),
      s => {
        if (assertors().isEmpty)
          Left(new OneTimeJobForm(form.withError("assertor", "No assertor selected", "error"), assertorParameters())) // TODO
        else
          Right(new ValidOneTimeJobForm(form, s, assertorParameters()))
      }
    )

    vsform
  }

  def blank: OneTimeJobForm = new OneTimeJobForm(playForm, AssertorsConfiguration.default)

  def fill(job: Job) = new ValidOneTimeJobForm(
    playForm fill(
      job.name,
      job.strategy.entrypoint,
      Otoj250, // TODO
      false
    ), (
      job.name,
      job.strategy.entrypoint,
      Otoj250, // TODO
      false
    ), job.strategy.assertorsConfiguration
  )

  private def playForm: Form[OneTimeJobType] = Form(
    tuple(
      "name" -> nonEmptyText,
      "entrypoint" -> of[URL],
      "otoj" -> of[OTOJType],
      "terms" -> of[Boolean](booleanFormatter).verifying("not_accepted", _ == true)
    )
  )

}

case class OneTimeJobForm private[view](
    form: Form[OneTimeJobType],
    val assertorsConfiguration: AssertorsConfiguration) extends VSForm {

  def apply(s: String) = form(s)

  def errors: Seq[(String, String)] = form.errors.map{case error => ("error", Messages(s"form.${error.key}.${error.message}"))}

  def withError(key: String, message: String) = copy(form = form.withError(key, message))

  def withErrors(errors: Seq[(String, String)]) = copy(form = form.copy(errors = errors.map(e => FormError(e._1, e._2)) ++ form.errors))

  def hasAssertor(assertor: String): Boolean = try {
    assertorsConfiguration.contains(AssertorId(assertor))
  } catch { case _: Exception =>
    false
  }

}

class ValidOneTimeJobForm private[view](
    form: Form[OneTimeJobType],
    bind: OneTimeJobType,
    assertorsConfiguration: AssertorsConfiguration) extends OneTimeJobForm(form, assertorsConfiguration) with VSForm {

  val (name, entrypoint, otoj, terms) = bind

  def createJob(user: User)(implicit conf: VSConfiguration): Job = {
    val strategy = Strategy(
      entrypoint = org.w3.util.URL(entrypoint),
      linkCheck = false,
      filter = Filter.includePrefix(entrypoint.toString), // Tom: non persisté de toute façon
      maxResources = otoj.maxPages,
      assertorsConfiguration = assertorsConfiguration)
    Job.createNewJob(name, strategy, user.id)
  }

}
