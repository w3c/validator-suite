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

import play.api.data
import play.api.i18n.Messages
import scala.concurrent.duration.Duration

import PasswordForm.PasswordType

object PasswordForm {

  type PasswordType = (String, String, String)

  import ExecutionContext.Implicits.global

  def create(user: User): Form[PasswordType] = Form(
    tuple(
      "p_current" -> nonEmptyText,
      "p_new" -> nonEmptyText(minLength = 6),
      "p_new2" -> nonEmptyText(minLength = 6)
    ).verifying("password.dont_match", p => p._2 == p._3)
      .verifying("application.invalidPassword", {p =>
      implicit val c = Global.conf
      Await.result(model.User.authenticate(user.email, p._1).map(_ => true).recover{case _ => false}, Duration("3s"))
    })
  )

}

