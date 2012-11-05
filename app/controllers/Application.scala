package controllers

import org.w3.vs.controllers._
import org.w3.vs.exception._
import org.w3.vs.model._
import org.w3.vs.view.form._
import play.api.i18n._
import play.api.mvc._
import scala.Some
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Application extends VSController {
  
  val logger = play.Logger.of("org.w3.vs.controllers.Application")

  def index: ActionA = Action { Redirect(routes.Jobs.index.toString) }

  def login: ActionA = Action { implicit req =>
    AsyncResult {
      getUser map {
        case _ => Redirect(routes.Jobs.index) // Already logged in -> redirect to index
      } recover {
        case  _: UnauthorizedException => Ok(views.html.login(LoginForm.blank)).withNewSession
      } recover toError
    }
  }
  
  def logout: ActionA = Action {
    Redirect(routes.Application.login).withNewSession.flashing("success" -> Messages("application.loggedOut"))
  }
  
  def authenticate: ActionA = Action { implicit req =>
    AsyncResult {
      (for {
        form <- Future.successful(LoginForm.bind() match {
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
        case InvalidFormException(form: LoginForm) => BadRequest(views.html.login(form))
      } recover toError
    }
  }
  
}
