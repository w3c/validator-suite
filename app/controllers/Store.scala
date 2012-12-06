package controllers

import org.w3.vs.controllers._
import org.w3.vs.exception._
import org.w3.vs.model._
import org.w3.vs.view.form._
import play.api.i18n._
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.w3.util.Util._
import com.yammer.metrics.Metrics
import java.util.concurrent.TimeUnit.{ MILLISECONDS, SECONDS }
import org.w3.vs.view.OTOJType

object Store extends VSController {

  val logger = play.Logger.of("org.w3.vs.controllers.Admin")

  def newOTOJ: ActionA = AsyncAction { implicit req =>
    val f: Future[PartialFunction[Format, Result]] =
      for {
        user <- getUser()
      } yield {
        case Html(_) => Ok(views.html.otojForm(OneTimeJobForm.blank, Some(user)))
      }
    f recover {
      case Unauthenticated => {
        case Html(_) => Ok(views.html.otojForm(OneTimeJobForm.blank, None))
      }
    }
  }

  def purchase(): ActionA = AsyncAction { implicit req =>
    val f1: Future[PartialFunction[Format, Result]] = for {
      // Authenticate or register user
      user <- getUser() recoverWith { case Unauthenticated =>
        // If user is not already logged in check the form
        req.body.asFormUrlEncoded.get.get("userType").map(_.headOption).flatten match {
          // New user
          case Some("new") =>
            for {
              form <- Future(RegisterForm.bind() match {
                case Left(form) => println(form.errors); throw InvalidFormException(form)
                case Right(validForm) => validForm
              })
              user <- User.register(email = form.email, name = form.name, password = form.password)
            } yield user
          // Known user
          case _ =>
            for {
              form <- Future(LoginForm.bind() match {
                case Left(form) => throw InvalidFormException(form)
                case Right(validForm) => validForm
              })
              user <- User.authenticate(form.email, form.password)
            } yield user
        }
      }
      form <- Future(OneTimeJobForm.bind match {
        case Left(form) => throw InvalidFormException(form)
        case Right(validJobForm) => validJobForm
      })
      job <- form.createJob(user).save()
    } yield {
      // LOG something !!
      case Html(_) => {
        Redirect("https://sites.fastspring.com/ercim/instant/otoj" + form.otoj.index + "?referrer=" + job.id).withSession("email" -> user.vo.email)
      }
    }
    f1 recover {
      case  _: UnauthorizedException => {
        case Html(_) => Unauthorized(views.html.otojForm(OneTimeJobForm.blank.withError("error", Messages("application.invalidCredentials")), None)).withNewSession
        case _ => Unauthorized
      }
      case InvalidFormException(form: VSForm, user) => {
        case Html(_) => {
          form match {
            case form: OneTimeJobForm => BadRequest(views.html.otojForm(form, user))
            case form: RegisterForm => BadRequest(views.html.otojForm(OneTimeJobForm.blank.withErrors(form.errors), user))
            case form: LoginForm => BadRequest(views.html.otojForm(OneTimeJobForm.blank.withErrors(form.errors), user))
          }
        }
        case _ => BadRequest
      }
    }
  }

}
