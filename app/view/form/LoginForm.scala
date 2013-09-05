package org.w3.vs.view.form

import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.i18n.Messages
import play.api.mvc._
import scala.concurrent._

object LoginForm {

  def bind()(implicit req: Request[_], context: ExecutionContext): Either[LoginForm, ValidLoginForm] = {
    val form = playForm.bindFromRequest
    form.fold(
      f => Left(new LoginForm(f)),
      s => Right(new ValidLoginForm(form, s))
    )
  }

  def blank: LoginForm = new LoginForm(playForm)

  private def playForm: Form[(String, String)] = Form(
    tuple(
      "l_email" -> email.verifying(nonEmpty),
      "l_password" -> nonEmptyText
    )
  )

  def apply(email: String) = {
    new LoginForm(playForm.fill(email, ""))
  }

}

class LoginForm private[view](form: Form[(String, String)]) extends VSForm {

  def apply(s: String) = form(s)

  def withError(key: String, message: String) = new LoginForm(form = form.withError(key, message))
  def withGlobalError(message: String) = {
    new LoginForm(form = form.withGlobalError(message))
  }

  def globalErrors: Seq[(String, String)] = {
    form.globalErrors.map{
      case error => ("error", Messages(error.message))
    }
  }

  def errors: Seq[(String, String)] = {
    form.errors.map{
      case error => ("error", Messages("form." + error.key + "." + error.message))
    }
  }

}

class ValidLoginForm private[view](form: Form[(String, String)], bind: (String, String)) extends LoginForm(form) with VSForm {
  val (email, password) = bind
}
