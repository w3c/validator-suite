package controllers

import org.w3.vs.view.form.{PasswordForm, JobForm, UserForm}
import org.w3.vs.controllers._
import org.w3.vs.{Metrics, model}
import scala.concurrent.Future
import org.w3.vs.exception.InvalidFormException
import play.api.i18n.Messages


object User extends VSController {

  val logger = play.Logger.of("controllers.User")

  import scala.concurrent.ExecutionContext.Implicits.global

  def profile = AuthenticatedAction("back.account") { implicit req => user =>
    Ok(views.html.profile(UserForm.forUser(user), PasswordForm.create(user), user))
  }

  def editAction: ActionA = AuthenticatedAction("form.editAccount") { implicit req => user =>
    (for {
      form <- Future(UserForm.bind match {
        case Left(form) => throw new InvalidFormException(form)
        case Right(validJobForm) => validJobForm
      })
      _ <- model.User.update(form.updateUser(user))
    } yield {
      logger.info(s"""id=${user.id} action=editprofile message="profile updated" """)
      render {
        case Accepts.Html() => SeeOther(routes.User.profile().url).withSession(("email" -> form.email)).flashing(("success" -> Messages("user.profile.updated")))
        case Accepts.Json() => Ok
      }
    }) recover {
      case InvalidFormException(form: UserForm, _) => {
        Metrics.form.editAccountFailure()
        render {
          case Accepts.Html() => BadRequest(views.html.profile(form, PasswordForm.create(user), user))
          case Accepts.Json() => BadRequest
        }
      }
    }
  }

  def changePasswordAction = AuthenticatedAction("form.editPassword") { implicit req => user =>
    PasswordForm.create(user).bindFromRequest().fold (
      formWithErrors => {
        Metrics.form.editPasswordFailure()
        BadRequest(views.html.profile(UserForm.forUser(user), formWithErrors, user))
      },
      bind => {
        val newPassword = bind._2
        for {
          saved <- model.User.update(user.withPassword(newPassword))
        } yield {
          logger.info(s"""id=${user.id} action=editpassword message="password updated" """)
          SeeOther(routes.User.profile().url).flashing(("success" -> Messages("user.password.updated")))
        }
      }
    )
  }

}
