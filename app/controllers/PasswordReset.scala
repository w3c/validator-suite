package controllers

import org.w3.vs.model.PasswordResetId
import org.w3.vs.{Emails, model}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.cache.Cache
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.exception.UnknownUser

object PasswordReset extends VSController {

  def logger = play.Logger.of("controllers.PasswordReset")

  val resetRequestForm: Form[String] = Form(("reset_email" -> email))

  val resetForm: Form[(String, String, String)] = Form(
    tuple(
      "reset_email" -> email,
      "reset_password" -> nonEmptyText(minLength = 6),
      "reset_password2" -> nonEmptyText(minLength = 6)
    ).verifying("password.dont_match", p => p._2 == p._3)
  )

  def resetRequest() = UserAwareAction("front.resetRequest") { implicit req => user =>
    user match {
      case Some(user) => SeeOther(routes.User.profile().url)
      case _ => Ok(views.html.resetRequest(resetRequestForm))
    }
  }

  def resetRequestAction() = AsyncAction("front.form.resetRequest") { implicit req =>
    resetRequestForm.bindFromRequest().fold(
      form => BadRequest(views.html.resetRequest(form)),
      email => (for {
          user <- model.User.getByEmail(email)
        } yield {
          val id = PasswordResetId()
          Cache.set(id.toString, user, vs.config.getInt("vs.emails.resetCacheExpire").getOrElse(3600))
          vs.sendEmail(Emails.resetPassword(user, id))
          logger.info(s"""id=${user.id} action=reset email=${user.email} token=${id} message="Reset confirmation sent" """)
          SeeOther(routes.PasswordReset.resetRequest().url).flashing(("success" -> Messages("resetRequestSuccess")))
        }) recover {
          case UnknownUser(_) =>
            logger.warn(s"""action=reset email=${email} message="Unknown email address" """)
            SeeOther(routes.PasswordReset.resetRequest().url).flashing(("error" -> Messages("resetRequestError")))
        }
    )
  }

  def reset(id: PasswordResetId) = AsyncAction("front.reset") { implicit req =>
    Cache.getAs[model.User](id.toString) match {
      case Some(user) => Ok(views.html.reset(resetForm, id))
      case _ => SeeOther(routes.PasswordReset.resetRequest().url).flashing(("error" -> Messages("resetError")))
    }
  }

  def resetAction(id: PasswordResetId) = AsyncAction("front.form.reset") { implicit req =>
    resetForm.bindFromRequest().fold(
      form => BadRequest(views.html.reset(form, id)),
      bind => {
        val (email, password) = (bind._1, bind._2)
        Cache.getAs[model.User](id.toString) match {
          case Some(user) if user.email == email => {
            for {
              _ <- model.User.update(user.withPassword(password))
            } yield {
              Cache.remove(id.toString)
              logger.info(s"""id=${user.id} action=reset email=${email} token=${id} message="Password updated" """)
              SeeOther(routes.User.profile().url)
                .flashing(("success" -> Messages("resetActionSuccess")))
                .withSession(("email" -> user.email))
            }
          }
          case Some(user) => {
            logger.warn(s"""id=${user.id} action=reset email=${email} token=${id} message="Email does not match this account" """)
            SeeOther(routes.PasswordReset.resetRequest().url).flashing(("error" -> Messages("resetActionEmailError")))
          }
          case _ => {
            //Cache.remove(id.toString)
            logger.warn(s"""action=reset token=${id} message="Invalid token" """)
            SeeOther(routes.PasswordReset.resetRequest().url).flashing(("error" -> Messages("resetActionError")))
          }
        }
      }
    )
  }

}
