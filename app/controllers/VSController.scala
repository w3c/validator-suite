package controllers

import org.w3.vs.exception._
import org.w3.vs.model.User
import org.w3.vs.view.form.LoginForm
import play.Logger.ALogger
import play.api.Play._
import play.api.cache.Cache
import play.api.i18n.Messages
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.JsValue
import play.api.mvc._
import scala.Some
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait VSController extends Controller {

  val logger: ALogger

  // TODO: make the implicit explicit!!!
  implicit val configuration: org.w3.vs.VSConfiguration = org.w3.vs.Prod.configuration
  implicit val system = configuration.system

  type ActionA = Action[AnyContent]

  def CloseWebsocket = (Iteratee.ignore[JsValue], Enumerator.eof)

  def isAjax(implicit reqHeader: RequestHeader) = {
    reqHeader.headers get ("x-requested-with") match {
      case Some("XMLHttpRequest") => true
      case _ => false
    }
  }

  def getUser()(implicit reqHeader: RequestHeader): Future[User] = {
    for {
      email <- Future(session.get("email").get) recoverWith { case _ => Future(throw Unauthenticated) }
      user <- Future(Cache.getAs[User](email).get) recoverWith { case _ => User.getByEmail(email) }
    } yield {
      Cache.set(email, user, current.configuration.getInt("cache.user.expire").getOrElse(300))
      user
    }
  }

  def AuthAsyncResult(f: User => Future[Result])(implicit reqHeader: RequestHeader): AsyncResult = {
    AsyncResult {
      getUser().flatMap(f(_)).recover(toError)
    }
  }

  def AuthAsyncAction(f: Request[AnyContent] => User => Future[Result]): ActionA = Action { implicit req =>
    AsyncResult {
      (for {
        user <- getUser()
        result <- f(req)(user)
      } yield result.withHeaders(("Cache-Control", "no-cache, no-store"))).recover(toError)
    }
  }

  def toError(implicit reqHeader: RequestHeader): PartialFunction[Throwable, Result] = {
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
  }

}