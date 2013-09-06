package controllers

import org.w3.vs.controllers._
import org.w3.vs.exception._
import org.w3.vs.model._
import org.w3.vs.view.form._
import play.api.i18n._
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.w3.vs.util.timer._
import com.codahale.metrics._
import org.w3.vs.Graphite
import play.api.templates.Html

object Application extends VSController {
  
  val logger = play.Logger.of("org.w3.vs.controllers.Application")

  def faq:     ActionA = Action { Ok(views.html.faq()) }
  def index:   ActionA = Action { Ok(views.html.index()) }
  def pricing: ActionA = Action { Ok(views.html.pricing()) }

  val loginName = (new controllers.javascript.ReverseApplication).login.name
  val loginTimer = Graphite.metrics.timer(MetricRegistry.name(Application.getClass, loginName))

  def login: ActionA = Action { implicit req =>
    AsyncResult {
      val f = getUser map {
        case _ => Redirect(routes.Jobs.index) // Already logged in -> redirect to index
      } recover {
        case  _: UnauthorizedException => Ok(views.html.loginRegister()).withNewSession
      } recover toError
      f.timer(loginName).timer(loginTimer)
    }
  }

  def register = login

  def logout: ActionA = Action {
    Redirect(routes.Application.index).withNewSession // .flashing("success" -> Messages("application.loggedOut"))
  }

  val authenticateName = (new controllers.javascript.ReverseApplication).loginAction.name
  val authenticateTimer = Graphite.metrics.timer(MetricRegistry.name(Application.getClass, authenticateName))
  
  def loginAction: ActionA = Action { implicit req =>
    AsyncResult {
      val f = (for {
        form <- Future(LoginForm.bind() match {
          case Left(form) => throw InvalidFormException(form)
          case Right(validForm) => validForm
        })
        user <- User.authenticate(form.email, form.password)
      } yield {
        (for {
          body <- req.body.asFormUrlEncoded
          param <- body.get("uri")
          uri <- param.headOption
        } yield uri) match {
          case Some(uri) if (uri != routes.Application.login.url && uri != "") => {
            SeeOther(uri).withSession("email" -> user.email)
          } // Redirect to "uri" param if specified
          case _ => SeeOther(routes.Jobs.index).withSession("email" -> user.email)
        }
      }) recover {
        case UnauthorizedException(email) =>
          Unauthorized(views.html.loginRegister(
            loginForm = LoginForm(email).withGlobalError("application.invalidCredentials")
          )).withNewSession
        case InvalidFormException(form: LoginForm, _) =>
          BadRequest(views.html.loginRegister(loginForm = form))
      } recover toError
      f.timer(authenticateName).timer(authenticateTimer)
    }
  }

  def registerAction: ActionA = Action { implicit req =>
    AsyncResult {
      RegisterForm.bind() match {
        case Left(form) => {
          Future.successful(BadRequest(views.html.loginRegister(registerForm = form)))
        }
        case Right(form) => {
          val f = User.register(name = form.name, email = form.email, password = form.password, isSubscriber = false).map {
            case user => {
              SeeOther(routes.Jobs.index).withSession("email" -> user.email)
            }
          } recover {
            case DuplicatedEmail(email: String) => BadRequest(views.html.loginRegister(registerForm = form.withError("r_email", "duplicate")))
          }
          f
        }
      }
    }
  }
  
}
