package org.w3.vs.view

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
import org.w3.util._
import play.api.i18n.Messages
import scalaz._
import akka.dispatch.ExecutionContext
import java.util.concurrent.TimeoutException
import org.w3.vs.exception._
import org.w3.util._
import org.w3.vs.controllers._
import org.w3.vs.exception._
import org.w3.vs.model._
import org.w3.vs.view._
import org.w3.vs._

sealed trait VSForm

class LoginForm private [view] (form: Form[(String, String)]) extends VSForm {
  def apply(s: String) = form(s)
  def globalError = form.globalError
}
class ValidLoginForm private [view] (form: Form[(String, String)], bind: (String, String)) extends LoginForm(form) with VSForm {
  val (email, password) = bind
}

object LoginForm {
  
  def bind()(implicit req: Request[_], context: ExecutionContext): FutureVal[LoginForm, ValidLoginForm] = {
    val form = playForm.bindFromRequest
    implicit def onTo(to: TimeoutException): LoginForm = new LoginForm(form.withError("key", Messages("error.timeout")))
    FutureVal.validated[LoginForm, ValidLoginForm](
      form.fold(
        f => Failure(new LoginForm(f)),
        s => Success(new ValidLoginForm(form, s))
      )
    )
  }
  
  def blank: LoginForm = new LoginForm(playForm)
  
  private def playForm: Form[(String, String)] = Form(
    tuple(
      "email" -> email,
      "password" -> text
    )
  )
  
}

class JobForm private [view] (
    form: Form[(String, URL, Int, Boolean, Int)]) extends VSForm {
  def apply(s: String) = form(s)
}

class ValidJobForm private [view] (
    form: Form[(String, URL, Int, Boolean, Int)],
    bind: (String, URL, Int, Boolean, Int)) extends JobForm(form) with VSForm {
  
  def globalError = form.globalError
  
  val (name, url, distance, linkCheck, maxNumberOfResources) = bind
  
  def createJob(user: User)(implicit conf: VSConfiguration): Job = {
    Job(
      name = name,
      organizationId = user.organizationId,
      creatorId = user.id,
      strategy = Strategy(
        entrypoint = url,
        distance = distance,
        linkCheck = linkCheck,
        maxNumberOfResources = maxNumberOfResources,
        filter = Filter(include = Everything, exclude = Nothing)))
  }
  
  def update(job: Job)(implicit conf: VSConfiguration): Job = {
    job.copy(
        name = name,
        strategy = job.strategy.copy(
            entrypoint = url, 
            distance = distance, 
            linkCheck = linkCheck, 
            maxNumberOfResources = maxNumberOfResources
        )
    )
  }
  
}

object JobForm {
  
  def bind(user: User, idOpt: Option[JobId])(implicit req: Request[_], context: ExecutionContext): FutureVal[JobForm, ValidJobForm] = {
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
  
  def fill(job: Job) = new ValidJobForm (
      playForm fill (
          job.name, 
          job.strategy.entrypoint, 
          job.strategy.distance, 
          job.strategy.linkCheck, 
          job.strategy.maxNumberOfResources
      ),(
          job.name, 
          job.strategy.entrypoint, 
          job.strategy.distance, 
          job.strategy.linkCheck, 
          job.strategy.maxNumberOfResources
      )
    )
  
  private def playForm: Form[(String, URL, Int, Boolean, Int)] = Form(
    tuple (
      "name" -> text,
      "url" -> of[URL],
      "distance" -> of[Int],
      "linkCheck" -> of[Boolean](booleanFormatter),
      "maxNumberOfResources" -> of[Int]
    )
  )
  
}
