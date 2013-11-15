package org.w3.vs.view.form

import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.i18n.Messages
import play.api.mvc._
import scala.concurrent._
import controllers.routes

case class Login(
  email: String = "",
  password: String = "",
  redirectUri: String = routes.Jobs.index().url)

object LoginForm {

  def apply(): Form[Login] = Form(
    mapping(
      "l_email" -> email.verifying(nonEmpty),
      "l_password" -> nonEmptyText,
      "uri" -> text
    )(Login.apply)(Login.unapply)
  ).fill(Login())

  /*
  {
      case (email, password, uri) =>
        /*val redirectUri = if (uri == "" || uri == routes.Application.login().url) {
          routes.Jobs.index().url
        } else {
          uri
        }*/
        Login(email, password, uri)
    }
   */

  /*def redirectTo(uri: String) = {
    apply().fill(Login(uri = uri))
  }*/

}

/*class LoginForm private[view](form: Form[(String, String, String)]) extends VSForm {

  def apply(s: String) = form(s)

  def fill(m: (String, String, String)) = new LoginForm(form.fill(m))

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

class ValidLoginForm private[view](form: Form[(String, String, String)], bind: (String, String, String)) extends LoginForm(form) with VSForm {
  val (email, password, redirectUri) = bind
}
  */