package controllers

import play.api._
import play.api.mvc._
import play.api.i18n._
import play.api.data.Form
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.Play.current
import akka.dispatch.Future
import org.w3.util._
import org.w3.util.Pimps._
import org.w3.vs.controllers._
import org.w3.vs.exception._
import org.w3.vs.model._
import org.w3.vs.actor._
import org.w3.vs.actor.message._
import scalaz._
import Scalaz._
import Validation._
import akka.util.Duration
import akka.util.duration._
import akka.dispatch.Await
import akka.dispatch.Future
import java.util.concurrent.TimeUnit._
import play.api.cache.Cache

object Application extends Controller {
  
  implicit def configuration = org.w3.vs.Prod.configuration
  
  def login = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          user <- getAuthenticatedUser() failMap {
            case e: StoreException => toResult(None)(e)
            case _ => Ok(views.html.login(loginForm)).withNewSession
          }
        } yield {
          Redirect(routes.Jobs.index) // If the user is already logged in send him to the dashboard
        }
      futureResult.expiresWith(FutureTimeoutError, 3, SECONDS).toPromise
    }
  }

  def logout = Action {
    Redirect(routes.Application.login).withNewSession.flashing("success" -> Messages("application.loggedOut"))
  }

  def authenticate = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          userF <- isValidForm(loginForm).toImmediateValidation failMap { formWithErrors => BadRequest(views.html.login(formWithErrors)) }
          user <- User.authenticate(userF._1, userF._2) failMap {
            case Unknown => toResult(None)(Unknown)
            case other => Unauthorized(views.html.login(loginForm, List(("error", Messages("application.invalidCredentials"))))).withNewSession
          }
        } yield {
          (for {
            body <- req.body.asFormUrlEncoded
            param <- body.get("uri")
            uri <- param.headOption
          } yield {
            SeeOther(uri).withSession("email" -> user.email)
          }).getOrElse(SeeOther(routes.Jobs.index.toString).withSession("email" -> user.email))
        }
      futureResult.expiresWith(FutureTimeoutError, 3, SECONDS).toPromise
    }
  }
  
  import org.w3.util.FutureValidation._

  def getAuthenticatedUser()(implicit session: Session): FutureValidationNoTimeOut[SuiteException, User] = {
    for {
      email <- immediateValidation { session.get("email").toSuccess(Unauthenticated) }
      user <- Cache.getAs[User](email) match {
        case Some(user) => Success(user).toImmediateValidation
        case _ => for {
          user <- User getByEmail (email)
        } yield {
          Cache.set(email, user, current.configuration.getInt("cache.user.expire").getOrElse(300))
          user
        }
      }
    } yield user
  }
  
  def getAuthenticatedUserOrResult()(implicit req: Request[_]): FutureValidationNoTimeOut[Result, User] = {
    getAuthenticatedUser failMap toResult(None)
  }
  
  def FutureTimeoutError(implicit req: Request[_]) = {
    InternalServerError(views.html.error(List(("error", Messages("error.timeout")))))
  }

  def toResult(authenticatedUserOpt: Option[User] = None)(e: SuiteException)(implicit req: Request[_]): Result = {
    e match {
      case  _@ (UnknownJob | UnauthorizedJob) => if (isAjax) NotFound(views.html.libs.messages(List(("error" -> Messages("jobs.notfound"))))) else SeeOther(routes.Jobs.index.toString).flashing(("error" -> Messages("jobs.notfound")))
      case _@ (UnknownUser | Unauthenticated) => Unauthorized(views.html.login(loginForm, List(("error" -> Messages("application.unauthorized"))))).withNewSession
      case                  StoreException(t) => InternalServerError(views.html.error(List(("error", Messages("exceptions.store", t.getMessage))), authenticatedUserOpt))
      case                      Unexpected(t) => InternalServerError(views.html.error(List(("error", Messages("exceptions.unexpected", t.getMessage))), authenticatedUserOpt))
    }
  }
  
}
