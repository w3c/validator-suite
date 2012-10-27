package controllers

import org.w3.vs.exception._
import org.w3.vs.model._
import org.w3.vs.view.form._
import play.api.i18n._
import play.api.mvc._
import scala.Some
import scala.concurrent.ExecutionContext.Implicits.global

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
      val f = for {
        form <- LoginForm.bind() map {
          case Left(form) => throw ForceResult(BadRequest(views.html.login(form)))
          case Right(validForm) => validForm
        }
        user <- User.authenticate(form.email, form.password) recover {
          case _: UnauthorizedException => throw ForceResult(Unauthorized(views.html.login(LoginForm.blank, List(("error", Messages("application.invalidCredentials"))))).withNewSession)
        }
      } yield {
        (for {
          body <- req.body.asFormUrlEncoded
          param <- body.get("uri")
          uri <- param.headOption
        } yield uri) match {
          case Some(uri) => SeeOther(uri).withSession("email" -> user.vo.email) // Redirect to "uri" param if specified
          case None => SeeOther(routes.Jobs.index.toString).withSession("email" -> user.vo.email)
        }
      }
      f recover toError
    }
  }

  /*def redirectWithSlash(a: Any): ActionA = Action { req =>
    MovedPermanently(
      req.path + "/" + Helper.queryString(req.queryString)
    )
  }*/
  
//  def toResult(authenticatedUserOpt: Option[User] = None)(e: SuiteException)(implicit req: Request[_]): Result = {
//    e match {
//      case  _@ (UnknownJob | UnauthorizedJob) => if (isAjax) NotFound(views.html.libs.messages(List(("error" -> Messages("jobs.notfound"))))) else SeeOther(routes.Jobs.index.toString).flashing(("error" -> Messages("jobs.notfound")))
//      case _@ (UnknownUser | Unauthenticated) => Unauthorized(views.html.login(loginForm, List(("error" -> Messages("application.unauthorized"))))).withNewSession
//      case                  StoreException(t) => InternalServerError(views.html.error(List(("error", Messages("exceptions.store", t.getMessage))), authenticatedUserOpt))
//      case                      Unexpected(t) => InternalServerError(views.html.error(List(("error", Messages("exceptions.unexpected", t.getMessage))), authenticatedUserOpt))
//    }
//  }
  
}
