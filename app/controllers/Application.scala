package controllers

import play.api._
import play.api.mvc._
import play.api.data.Form
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
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

object Application extends Controller {
  
  implicit def configuration = org.w3.vs.Prod.configuration
  
  def login = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          user <- getAuthenticatedUser() failMap {
            case e: StoreException => InternalServerError(views.html.error(List(("error", "Store Exception: " + e.t.getMessage()))))
            case _ => Ok(views.html.login(loginForm)).withNewSession
          }
        } yield {
          Redirect(routes.Jobs.index) // If the user is already logged in send him to the dashboard
        }
      futureResult.expiresWith(InternalServerError, 1, SECONDS).toPromise
    }
  }

  def logout = Action {
    Redirect(routes.Application.login).withNewSession.flashing("success" -> "You've been logged out")
  }

  def authenticate = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          userF <- isValidForm(loginForm).toImmediateValidation failMap { formWithErrors => BadRequest(views.html.login(formWithErrors)) }
          userO <- User.authenticate(userF._1, userF._2).failMap{e => InternalServerError(views.html.error(List(("error", "Store Exception: " + e.t.getMessage()))))}
          user <- userO.toSuccess(Unauthorized(views.html.login(loginForm)).withNewSession).toImmediateValidation
        } yield {
          Redirect(routes.Jobs.index).withSession("email" -> user.email)
        }
      futureResult.expiresWith(InternalServerError, 1, SECONDS).expiresWith(InternalServerError, 1, SECONDS).toPromise
    }
  }
  
  // TODO
  // https://github.com/playframework/Play20/wiki/Scalacache
  
  def getAuthenticatedUser()(implicit session: Session): FutureValidationNoTimeOut[SuiteException, User] = {
    for {
      email <- session.get("email").toSuccess(Unauthenticated).toImmediateValidation
      userO <- User getByEmail (email)
      user <- userO.toSuccess(UnknownUser).toImmediateValidation
    } yield user
  }
  
  def getAuthenticatedUserOrResult()(implicit req: Request[_]): FutureValidationNoTimeOut[Result, User] = {
    getAuthenticatedUser failMap toResult(None)
  }
  
  def toResult(authenticatedUserOpt: Option[User] = None)(e: SuiteException)(implicit req: Request[_]): Result = {
    e match {
      case  _@ (UnknownJob | UnauthorizedJob) => if (isAjax) NotFound(views.html.libs.messages(List(("error" -> "Job not found")))) else SeeOther(routes.Jobs.index.toString).flashing(("error" -> "Job not found"))
      case _@ (UnknownUser | Unauthenticated) => Unauthorized(views.html.login(loginForm)).withNewSession
      case                  StoreException(t) => InternalServerError(views.html.error(List(("error", "Store Exception: " + t.getMessage)), authenticatedUserOpt))
      case                      Unexpected(t) => InternalServerError(views.html.error(List(("error", "Unexpected Exception: " + t.getMessage)), authenticatedUserOpt))
    }
  }
  
}