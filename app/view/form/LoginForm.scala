package org.w3.vs.view.form

import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.data._
import play.api.mvc._
import play.api.i18n.Messages
import akka.dispatch.ExecutionContext
import org.w3.util.FutureVal
import java.util.concurrent.TimeoutException
import scalaz._

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

class LoginForm private[view](form: Form[(String, String)]) extends VSForm {

  def apply(s: String) = form(s)

  def errors: Seq[(String, String)] = form.errors.map{case error => ("error", /*error.key + */error.message)}
}

class ValidLoginForm private[view](form: Form[(String, String)], bind: (String, String)) extends LoginForm(form) with VSForm {
  val (email, password) = bind
}
