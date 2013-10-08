package org.w3.vs.view.form

import org.w3.vs.web.URL
import org.w3.vs.{Global, ValidatorSuite}
import org.w3.vs.model._
import org.w3.vs.view._
import play.api.data.Forms._
import play.api.data._
import play.api.data.format.Formats._
import play.api.data.format._
import play.api.mvc.{Filter => _, _}
import scala.concurrent._

import play.api.i18n.Messages
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

/*object JobForm {

  type JobType = (String, URL, Int)

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
      "maxPages" -> of[Int]
      //"terms" -> of[Boolean](checkboxFormatter).verifying("not_accepted", _ == true)
    ).verifying("invalid.entrypoint", { bind =>
      try {
        val code = Global.conf.httpClient.prepareGet(bind._2.toString).execute().get(10, TimeUnit.SECONDS).getStatusCode
        code >= 200 && code < 300
      } catch { case e: Exception =>
        false
      }
    })
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

  val (name, entrypoint, maxPages) = bind

  def createJob(user: User)(implicit conf: ValidatorSuite): Job = {
    val strategy = Strategy(
      entrypoint = org.w3.vs.web.URL(entrypoint),
      linkCheck = false,
      filter = Filter.includePrefix(entrypoint.toString),
      maxResources = maxPages,
      assertorsConfiguration = AssertorsConfiguration.default
    )
    Job(name = name, strategy = strategy, creatorId = Some(user.id), isPublic = false)
  }

}
 */

object JobForm {

  def apply(user: User): Form[Job] = Form(
    mapping(
      "name" -> nonEmptyText,
      "entrypoint" -> of[URL].verifying("invalid", { url =>
        try {
          // TODO use a dedicated httpClient?
          val code = Global.conf.httpClient.prepareGet(url.toString).execute().get(10, TimeUnit.SECONDS).getStatusCode
          code >= 200 && code < 300
        } catch { case e: Exception =>
          false
        }
      }),
      "maxPages" -> of[Int].verifying("creditMaxExceeded", { credits =>
        credits <= user.credits
      })
    )((name, entrypoint, maxPages) => {
      val strategy = Strategy(URL(entrypoint), maxPages)
      Job(name = name, strategy = strategy, creatorId = Some(user.id))
    })((job: Job) =>
      Some(job.name, job.strategy.entrypoint, job.strategy.maxResources)
    )
  )

}