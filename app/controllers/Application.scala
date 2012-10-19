package controllers

import play.api.mvc._
import play.api.i18n._
import play.api.Play.current
import org.w3.util._
import org.w3.vs.controllers._
import org.w3.vs.exception._
import org.w3.vs.model._
import org.w3.vs.view._
import org.w3.vs.view.form._
import scalaz._
import Scalaz._
import play.api.cache.Cache
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.banana._
import org.w3.vs.model.Redirect
import org.w3.vs.exception.ForceResult
import org.w3.vs.exception.UnknownJob
import play.api.mvc.AsyncResult
import scala.Some

object Application extends Controller {
  
  type ActionA = Action[AnyContent]
  
  val logger = play.Logger.of("org.w3.vs.controllers.Application")
  
  implicit private def configuration = org.w3.vs.Prod.configuration
  
  implicit def toError(implicit req: Request[_]): PartialFunction[Throwable, Result] = {
    // TODO timeout, store exception, etc...
    case ForceResult(result) => result
    case UnknownJob(id) => {
      if (isAjax) {
        NotFound(Messages("exceptions.job.unknown", id))
      } else {
        SeeOther(routes.Jobs.index.toString).flashing(("error" -> Messages("exceptions.job.unknown", id)))
      }
    }
    case _: UnauthorizedException =>
      Unauthorized(views.html.login(LoginForm.blank, List(("error", Messages("application.unauthorized"))))).withNewSession
    case t: Throwable => {
      logger.error("Unexpected exception: " + t.getMessage, t)
      InternalServerError(views.html.error(List(("error", Messages("exceptions.unexpected", t.getMessage)))))
    }
    case t => {
      logger.error("Unexpected exception: ", t)
      InternalServerError(views.html.error(List(("error", Messages("exceptions.unexpected", t.toString)))))
    }
  }
  
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

  def getUser()(implicit /*req: Request[_], */ session: Session): Future[User] = {
    for {
      email <- session.get("email").get.asFuture recoverWith { case _ => Future {
        throw Unauthenticated } }
      user <- Cache.getAs[User](email).get.asFuture recoverWith { case _ => User.getByEmail(email) }
    } yield {
      Cache.set(email, user, current.configuration.getInt("cache.user.expire").getOrElse(300))
      user
    }
  } 
  
  def redirectWithSlash(a: Any): ActionA = Action { req =>
    MovedPermanently(
      req.path + "/" + Helper.queryString(req.queryString)
    )
  }
  
//  def toResult(authenticatedUserOpt: Option[User] = None)(e: SuiteException)(implicit req: Request[_]): Result = {
//    e match {
//      case  _@ (UnknownJob | UnauthorizedJob) => if (isAjax) NotFound(views.html.libs.messages(List(("error" -> Messages("jobs.notfound"))))) else SeeOther(routes.Jobs.index.toString).flashing(("error" -> Messages("jobs.notfound")))
//      case _@ (UnknownUser | Unauthenticated) => Unauthorized(views.html.login(loginForm, List(("error" -> Messages("application.unauthorized"))))).withNewSession
//      case                  StoreException(t) => InternalServerError(views.html.error(List(("error", Messages("exceptions.store", t.getMessage))), authenticatedUserOpt))
//      case                      Unexpected(t) => InternalServerError(views.html.error(List(("error", Messages("exceptions.unexpected", t.getMessage))), authenticatedUserOpt))
//    }
//  }
  
}
