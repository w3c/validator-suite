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
import org.w3.util.FutureVal._
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
  
  type ActionA = Action[AnyContent]
  
  implicit def configuration = org.w3.vs.Prod.configuration
  
  def toError(t: Throwable)(implicit req: Request[_]): Result = {
    val error = t match {
      // TODO timeout, store exception, etc...
      case t: Throwable => Messages("exceptions.unexpected", t.getMessage)
    }
    InternalServerError(views.html.error(List(("error", error))))
  }
  
  def login: ActionA = Action { implicit req =>
    AsyncResult {
      (getUser map {
        _ => Redirect(routes.Jobs.index) // Already logged in -> redirect to index
      } failMap {
        case _@ (UnknownUser | Unauthenticated) => Ok(views.html.login(loginForm)).withNewSession
        case t => toError(t)
      }).toVSPromise
    }
  }

  def logout: ActionA = Action {
    Redirect(routes.Application.login).withNewSession.flashing("success" -> Messages("application.loggedOut"))
  }

  def authenticate: ActionA = Action { implicit req =>
    AsyncResult {
      (for {
        userF <- validateForm(loginForm) failMap { form => BadRequest(views.html.login(form)) }
        user <- User.authenticate(userF._1, userF._2) failMap {
          case _@ (UnknownUser | Unauthenticated) => Unauthorized(views.html.login(loginForm, List(("error", Messages("application.invalidCredentials"))))).withNewSession
          case t => toError(t)
        }
      } yield {
        (for {
          body <- req.body.asFormUrlEncoded
          param <- body.get("uri")
          uri <- param.headOption
        } yield uri) fold (
          uri => SeeOther(uri).withSession("email" -> user.email), // Redirect to "uri" param if specified
          SeeOther(routes.Jobs.index.toString).withSession("email" -> user.email)
        )
      }).toVSPromise
    }
  }
  
  def getUser()(implicit session: Session): FutureVal[Exception, User] = {
     for {
      email <- FutureVal
          .pure(session.get("email").get)
          .failWith(Unauthenticated)
      user <- FutureVal
          .pure(Cache.getAs[User](email).get)
          .flatMapFail {case _ => User.getByEmail(email)}
          .failWith(UnknownUser)
    } yield {
      Cache.set(email, user, current.configuration.getInt("cache.user.expire").getOrElse(300))
      user
    }
  } 

//
//  def toResult(authenticatedUserOpt: Option[User] = None)(e: SuiteException)(implicit req: Request[_]): Result = {
//    e match {
//      case  _@ (UnknownJob | UnauthorizedJob) => if (isAjax) NotFound(views.html.libs.messages(List(("error" -> Messages("jobs.notfound"))))) else SeeOther(routes.Jobs.index.toString).flashing(("error" -> Messages("jobs.notfound")))
//      case _@ (UnknownUser | Unauthenticated) => Unauthorized(views.html.login(loginForm, List(("error" -> Messages("application.unauthorized"))))).withNewSession
//      case                  StoreException(t) => InternalServerError(views.html.error(List(("error", Messages("exceptions.store", t.getMessage))), authenticatedUserOpt))
//      case                      Unexpected(t) => InternalServerError(views.html.error(List(("error", Messages("exceptions.unexpected", t.getMessage))), authenticatedUserOpt))
//    }
//  }
  
}
