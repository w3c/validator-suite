package org.w3.vs.view.form

import org.w3.vs.web.URL
import org.w3.vs.{Global, ValidatorSuite, model}
import org.w3.vs.assertor.Assertor
import org.w3.vs.model._
import org.w3.vs.view._
import play.api.data.Forms._
import play.api.data._
import play.api.data.format.Formats._
import play.api.data.format._
import play.api.mvc.{Filter => _, _}
import scala.concurrent._

import UserForm.UserType
import play.api.data
import play.api.i18n.Messages
import scala.concurrent.duration.Duration

object UserForm {

  type UserType = (String, String, Boolean)

  def bind()(implicit req: Request[AnyContent]): Either[UserForm, ValidUserForm] = {

    val form: Form[UserType] = playForm.bindFromRequest

    val vsform: Either[UserForm, ValidUserForm] = form.fold(
      f => Left(new UserForm(f)),
      s => Right(new ValidUserForm(form, s))
    )

    vsform
  }

  def blank: UserForm = new UserForm(playForm)

  def forUser(user: User) = new UserForm(playForm.fill((user.name, user.email, user.optedIn)))

  import ExecutionContext.Implicits.global

  private def playForm: Form[UserType] = Form(
    tuple(
      "u_userName" -> nonEmptyText,
      "u_email" -> email,
      //"u_password" -> nonEmptyText(minLength = 6),
      //"u_newPassword" -> text,
      //"u_newPassword2" -> text,
      "u_optedIn" -> of[Boolean](checkboxFormatter)
    )/*.verifying("password.dont_match", p => if(p._4!="") {p._4 == p._5} else true)
      .verifying("authentication.failed", {p =>
      implicit val c = Global.conf
      Await.result(model.User.authenticate(p._2, p._3).map(_ => true), Duration("3s"))
    })*/
  )

}

case class UserForm private[view](form: Form[UserType]) extends VSForm {

  def apply(s: String) = form(s)

  def errors: Seq[(String, String)] = form.errors.map {
    case error => ("error", Messages(s"form.${error.key}.${error.message}"))
  }

  def withError(key: String, message: String) = copy(form = form.withError(key, message))

  def withErrors(errors: Seq[(String, String)]) = copy(form = form.copy(errors = errors.map(e => FormError(e._1, e._2)) ++ form.errors))

}

class ValidUserForm private[view](
  form: Form[UserType],
  bind: UserType) extends UserForm(form) with VSForm {

  val (name, email, optedIn) = bind

  def updateUser(user: User): User = {
    user.copy(name = name, email = email, optedIn = optedIn)
  }

}
