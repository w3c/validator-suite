package org.w3.vs.view.form

import org.w3.util.URL
import org.w3.vs.VSConfiguration
import org.w3.vs.assertor.Assertor
import org.w3.vs.model._
import org.w3.vs.view._
import play.api.data.Forms._
import play.api.data.format._
import play.api.data.format.Formats._
import play.api.data.validation.Constraints._
import play.api.data._
import play.api.mvc.{ Filter => _, _ }
import scala.concurrent._
import play.api.i18n.Messages

object JobForm {

  type Type = (String, URL, Boolean, Int)

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

  def bind()(implicit req: Request[AnyContent], context: ExecutionContext): Either[JobForm, ValidJobForm] = {

    val form: Form[Type] = playForm.bindFromRequest

    val vsform: Either[JobForm, ValidJobForm] = form.fold(
      f => Left(new JobForm(f, assertorParameters())),
      s => {
        if (assertors().isEmpty)
          Left(new JobForm(form.withError("assertor", "required"), assertorParameters())) // TODO
        else
          Right(new ValidJobForm(form, s, assertorParameters()))
      }
    )

    vsform
  }

  def blank: JobForm = new JobForm(playForm, AssertorsConfiguration.default)

  def fill(job: Job) = new ValidJobForm(
    playForm fill(
      job.name,
      job.strategy.entrypoint,
      job.strategy.linkCheck,
      job.strategy.maxResources
    ), (
      job.name,
      job.strategy.entrypoint,
      job.strategy.linkCheck,
      job.strategy.maxResources
    ), job.strategy.assertorsConfiguration
  )

  private def playForm: Form[Type] = Form(
    tuple(
      "name" -> nonEmptyText,
      //"assertor" -> of[Seq[String]].verifying("Choose an assertor", ! _.isEmpty),
      "entrypoint" -> of[URL],
      "linkCheck" -> of[Boolean](booleanFormatter),
      "maxResources" -> number(min=1, max=5000)
    )
  )

}

class JobForm private[view](
    form: Form[(String, URL, Boolean, Int)],
    val assertorsConfiguration: AssertorsConfiguration) extends VSForm {

  def apply(s: String) = form(s)

  def errors: Seq[(String, String)] = form.errors.map{
    case error => ("error", Messages("form." + error.key + "." + error.message))
  }

  def hasAssertor(assertor: String): Boolean = try {
    assertorsConfiguration.contains(AssertorId(assertor))
  } catch { case _: Exception =>
    false
  }

}

class ValidJobForm private[view](
    form: Form[JobForm.Type],
    bind: JobForm.Type,
    assertorsConfiguration: AssertorsConfiguration) extends JobForm(form, assertorsConfiguration) with VSForm {

  val (name, entrypoint, linkCheck, maxResources) = bind

  def createJob(user: User)(implicit conf: VSConfiguration): Job = {
    val strategy = Strategy(
      entrypoint = org.w3.util.URL(entrypoint),
      linkCheck = linkCheck,
      filter = Filter.includePrefix(entrypoint.toString), // Tom: non persisté de toute façon
      maxResources = maxResources,
      assertorsConfiguration = assertorsConfiguration)
    Job.createNewJob(name, strategy, user.id)
  }

  def update(job: Job)(implicit conf: VSConfiguration): Job = {
    ??? // TODO decide, implement
    //     job.copy(
    //         name = name,
    //         strategy = job.strategy.copy(
    //             entrypoint = url,
    //             linkCheck = linkCheck,
    //             maxResources = maxResources
    //         )
    //     )
  }

}
