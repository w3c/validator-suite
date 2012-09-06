package org.w3.vs.view.form

import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.data._
import play.api.mvc._
import play.api.i18n.Messages
import org.w3.util.{FutureVal, URL}
import org.w3.vs.model._
import org.w3.vs.VSConfiguration
import org.w3.vs.controllers._
import akka.dispatch.ExecutionContext
import java.util.concurrent.TimeoutException
import scalaz._

object JobForm {

  def bind()(implicit req: Request[_], context: ExecutionContext): FutureVal[JobForm, ValidJobForm] = {
    val form = playForm.bindFromRequest
    implicit def onTo(to: TimeoutException): JobForm = new JobForm(form.withError("key", Messages("error.timeout")))
    FutureVal.validated[JobForm, ValidJobForm](
      form.fold(
        f => Failure(new JobForm(f)),
        s => Success(new ValidJobForm(form, s))
      )
    )
  }

  def blank: JobForm = new JobForm(playForm)

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
      )
  )

  private def playForm: Form[(String, URL, Boolean, Int)] = Form(
    tuple(
      "name" -> text,
      "url" -> of[URL],
      "linkCheck" -> of[Boolean](booleanFormatter),
      "maxResources" -> of[Int]
    )
  )

}

class JobForm private[view](
    form: Form[(String, URL, Boolean, Int)]) extends VSForm {
  def apply(s: String) = form(s)

  def globalError = form.globalError
}

class ValidJobForm private[view](
    form: Form[(String, URL, Boolean, Int)],
    bind: (String, URL, Boolean, Int)) extends JobForm(form) with VSForm {

  val (name, url, linkCheck, maxResources) = bind

  def createJob(user: User)(implicit conf: VSConfiguration): Job = {
    Job(
      name = name,
      organization = user.vo.organization.get, // TODO what if organization = None?
      creator = user.id,
      strategy = Strategy(
        entrypoint = url,
        linkCheck = linkCheck,
        filter = Filter.includePrefix(url.toString), // Tom: non persisté de toute façon
        maxResources = maxResources))
  }

  def update(job: Job)(implicit conf: VSConfiguration): Job = {
    null // TODO decide, implement
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
