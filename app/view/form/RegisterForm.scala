package org.w3.vs.view.form

import play.api.data.Forms._
import play.api.data._
import play.api.mvc._
import scala.concurrent._

import RegisterForm.RegisterType
import play.api.i18n.Messages

object RegisterForm {

  type RegisterType = (String, String, String, String)

  def bind()(implicit req: Request[_], context: ExecutionContext): Either[RegisterForm, ValidRegisterForm] = {
    val form = playForm.bindFromRequest
    form.fold(
      f => Left(new RegisterForm(f)),
      s => Right(new ValidRegisterForm(form, s))
    )
  }

  def blank: RegisterForm = new RegisterForm(playForm)

  private def playForm: Form[RegisterType] = Form(
    tuple(
      "userName" -> nonEmptyText,
      "email" -> email,
      "password" -> nonEmptyText(minLength = 6),
      "repeatPassword" -> text
    ).verifying("password.dont_match", p => p._3 == p._4)
  )

}

class RegisterForm private[view](form: Form[RegisterType]) extends VSForm {
  def apply(s: String) = form(s)
  def errors: Seq[(String, String)] =
    form.errors.map{case error => ("error", Messages("form." + error.key + "." + error.message))}
}

class ValidRegisterForm private[view](form: Form[RegisterType], bind: RegisterType) extends RegisterForm(form) with VSForm {
  val (name, email, password, repeatPassword) = bind
}
