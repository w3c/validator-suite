package org.w3.vs.view.form

import java.util.concurrent.TimeoutException
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.Messages
import play.api.mvc._
import scala.concurrent._

object RegisterForm {

  type RegisterType = (String, String, List[String])

  def bind()(implicit req: Request[_], context: ExecutionContext): Either[RegisterForm, ValidRegisterForm] = {
    val form = playForm.bindFromRequest
    implicit def onTo(to: TimeoutException): RegisterForm = new RegisterForm(form.withError("key", Messages("error.timeout")))
    form.fold(
      f => Left(new RegisterForm(f)),
      s => Right(new ValidRegisterForm(form, s))
    )
  }

  def blank: RegisterForm = new RegisterForm(playForm)

  private def playForm: Form[RegisterType] = Form(
    tuple(
      "name" -> nonEmptyText,
      "email" -> email,
      "password" -> list(nonEmptyText(minLength = 8)).verifying("passwords_dont_match", _.distinct.size == 1)
    )
  )

}

import RegisterForm.RegisterType

class RegisterForm private[view](form: Form[RegisterType]) extends VSForm {

  def apply(s: String) = form(s)

  def errors: Seq[(String, String)] = form.errors.map{case error => ("error", /*error.key + */error.message)}
}

class ValidRegisterForm private[view](form: Form[RegisterType], bind: RegisterType) extends RegisterForm(form) with VSForm {
  val (name, email, password) = bind
}
