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

object Application extends VSController {
  
  val logger = play.Logger.of("org.w3.vs.controllers.Application")

  def index: ActionA = Action { Redirect(routes.Jobs.index.toString) }

  val loginName = (new controllers.javascript.ReverseApplication).login.name
  val loginTimer = Metrics.newTimer(Application.getClass, loginName, MILLISECONDS, SECONDS)

  def login: ActionA = Action { implicit req =>
    AsyncResult {
      val f = getUser map {
        case _ => Redirect(routes.Jobs.index) // Already logged in -> redirect to index
      } recover {
        case  _: UnauthorizedException => Ok(views.html.login(LoginForm.blank)).withNewSession
      } recover toError
      f.timer(loginName).timer(loginTimer)
    }
  }

  def logout: ActionA = Action {
    Redirect(routes.Application.login).withNewSession.flashing("success" -> Messages("application.loggedOut"))
  }

  val authenticateName = (new controllers.javascript.ReverseApplication).authenticate.name
  val authenticateTimer = Metrics.newTimer(Application.getClass, authenticateName, MILLISECONDS, SECONDS)
  
  def authenticate: ActionA = Action { implicit req =>
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
          case Some(uri) => SeeOther(uri).withSession("email" -> user.vo.email) // Redirect to "uri" param if specified
          case None => SeeOther(routes.Jobs.index).withSession("email" -> user.vo.email)
        }
      }) recover {
        case  _: UnauthorizedException => Unauthorized(views.html.login(LoginForm.blank, List(("error", Messages("application.invalidCredentials"))))).withNewSession
        case InvalidFormException(form: LoginForm, _) => BadRequest(views.html.login(form))
      } recover toError
      f.timer(authenticateName).timer(authenticateTimer)
    }
  }

  def register: ActionA = Action { implicit req =>
    AsyncResult {
      getUser map {
        case _ => Redirect(routes.Jobs.index) // Already logged in -> redirect to index
      } recover {
        case  _: UnauthorizedException => Ok(views.html.register(RegisterForm.blank)).withNewSession
      } recover toError
    }
  }

  def registerAction: ActionA = Action { implicit req =>
    AsyncResult {
      (for {
        form <- Future(RegisterForm.bind() match {
          case Left(form) => throw InvalidFormException(form)
          case Right(validForm) => validForm
        })
        user <- User.register(email = form.email, name = form.name, password = form.password, false)
        // TODO The registration form should be protected. Registration for one-time users is done through its own form
        // and subscribers registration is done manually. If this form is protected for admins isSubscriber could be set to true
      } yield {
        SeeOther(routes.Jobs.index).withSession("email" -> user.vo.email)
      }) recover {
        case InvalidFormException(form: RegisterForm, _) => BadRequest(views.html.register(form))
      } recover toError
    }
  }
  
}
