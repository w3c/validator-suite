package controllers

import play.api.mvc._
import play.api.i18n._
import play.api.Play.current
import org.w3.util._
import org.w3.util.FutureVal._
import org.w3.vs.controllers._
import org.w3.vs.exception._
import org.w3.vs.model._
import org.w3.vs.view._
import scalaz._
import Scalaz._
import play.api.cache.Cache

object Application extends Controller {
  
  type ActionA = Action[AnyContent]
  
  val logger = play.Logger.of("org.w3.vs.controllers.Application")
  
  implicit def configuration = org.w3.vs.Prod.configuration
  
  implicit def toError(t: Throwable)(implicit req: Request[_]): Result = {
    t match {
      // TODO timeout, store exception, etc...
      case _: UnauthorizedException => Unauthorized(views.html.login(LoginForm.blank, List(("error", Messages("application.unauthorized"))))).withNewSession
      case t: Throwable => {
        logger.error("Unexpected exception: " + t.getMessage, t)
        InternalServerError(views.html.error(List(("error", Messages("exceptions.unexpected", t.getMessage)))))
      }
    }
  }
  
  def login: ActionA = Action { implicit req =>
    AsyncResult {
      getUser map {
        _ => Redirect(routes.Jobs.index) // Already logged in -> redirect to index
      } failMap {
        case _: UnauthorizedException => Ok(views.html.login(LoginForm.blank)).withNewSession
        case t => toError(t)
      } toPromise 
    }
  }
  
  def logout: ActionA = Action {
    Redirect(routes.Application.login).withNewSession.flashing("success" -> Messages("application.loggedOut"))
  }
  
  def authenticate: ActionA = Action { implicit req =>
    AsyncResult {
      (for {
        form <- LoginForm.bind() failMap (form => BadRequest(views.html.login(form)))
        user <- User.authenticate(form.email, form.password) failMap {
          case _: UnauthorizedException => Unauthorized(views.html.login(LoginForm.blank, List(("error", Messages("application.invalidCredentials"))))).withNewSession
          case t => toError(t)
        }
      } yield {
        (for {
          body <- req.body.asFormUrlEncoded
          param <- body.get("uri")
          uri <- param.headOption
        } yield uri) fold (
          uri => SeeOther(uri).withSession("email" -> user.vo.email), // Redirect to "uri" param if specified
          SeeOther(routes.Jobs.index.toString).withSession("email" -> user.vo.email)
        )
      }) toPromise
    }
  }
  
  def getUser()(implicit session: Session): FutureVal[Exception, User] = {
    for {
      email <- FutureVal.pure(session.get("email").get).failWith(Unauthenticated)
      user <- FutureVal.pure(Cache.getAs[User](email).get)
                .flatMapFail(_ => User.getByEmail(email))
    } yield {
      Cache.set(email, user, current.configuration.getInt("cache.user.expire").getOrElse(300))
      user
    }
  } 
  
  def redirectWithSlash(a: Any): ActionA = Action { req =>
    MovedPermanently(req.uri + "/")
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
