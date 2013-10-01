package controllers

import org.w3.vs.exception._
import org.w3.vs.controllers._
import org.w3.vs.model.User
import org.w3.vs.view.form.LoginForm
import play.Logger.ALogger
import play.api.Play._
import play.api.cache.Cache
import play.api.i18n.Messages
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.JsValue
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.mutable.LinkedHashMap
import org.w3.vs.exception.UnknownJob
import play.api.mvc.AsyncResult
import play.api.mvc.Call
import org.apache.commons.codec.binary.Base64.decodeBase64
import org.mindrot.jbcrypt.BCrypt
import org.w3.vs.{Graphite, Global}
import play.api.http.{MimeTypes, MediaRange}
import com.codahale.metrics.MetricRegistry

trait VSController extends Controller {

  def logger: ALogger

  implicit val conf = org.w3.vs.Global.conf

  implicit val system = conf.system

  def CloseWebsocket = (Iteratee.ignore[JsValue], Enumerator.eof)

  implicit def toAsynchronousResult(result: Result): Future[Result] = Future.successful(result)

  def getUser()(implicit reqHeader: RequestHeader): Future[User] = {
    for {
      email <- session.get("email") match {
          case Some(email) => Future.successful(email)
          case _ => Future.failed(Unauthenticated(""))
        }
      user <- User.getByEmail(email)
      //user <- Future(Cache.getAs[User](email).get) recoverWith { case _ => User.getByEmail(email) }
    } yield {
      Cache.set(email, user, current.configuration.getInt("cache.user.expire").getOrElse(300))
      user
    }
  }

  def getUserOption()(implicit reqHeader: RequestHeader): Future[Option[User]] = {
    getUser.map(Some(_)).recover{case _ => None}
  }

  def Timer(name: String)(f: Future[Result]): Future[Result] = {
    /*val timer = Graphite.metrics.timer(MetricRegistry.name(Application.getClass, name))
    Graphite.getTimer(name)
    f.timer(name).timer(loginTimer)*/
    f
  }

  def AsyncAction(f: Request[AnyContent] => Future[Result]) = Action { req =>
    Async {
      f(req) recover {
        case AccessNotAllowed => Global.onHandlerNotFound(req)
      }
    }
  }

  val AcceptsStream = Accepting(MimeTypes.EVENT_STREAM)

  def Authenticated(f: User => Future[Result])(implicit req: RequestHeader): Future[Result] = {
    (for {
      user <- getUser()
      result <- f(user)
    } yield result) recover {
      case UnauthorizedException(email) => {
        render {
          case Accepts.Html() =>
            Unauthorized(views.html.login(
              form = LoginForm.blank.fill(email, "", req.uri),
              messages = List(("error", Messages("application.unauthorized")))
            )).withNewSession
          case Accepts.Json() => Unauthorized
        }
      }
    }
  }

  def RootAction(f: Request[AnyContent] => User => Future[Result]): ActionA = AsyncAction { implicit req =>
    Authenticated { user =>
      user match {
        case user if user.isRoot => f(req)(user)
        case _ => throw AccessNotAllowed
      }
    }
  }

  def AuthenticatedAction(f: Request[AnyContent] => User => Future[Result]): ActionA =
    AsyncAction { implicit req => Authenticated { user => f(req)(user) } }

  def UserAware(f: Option[User] => Future[Result])(implicit req: RequestHeader): Future[Result] = {
    for {
      userO <- getUserOption()
      result <- f(userO)
    } yield result
  }

  def UserAwareAction(f: Request[AnyContent] => Option[User] => Future[Result]): ActionA =
    AsyncAction { implicit req => UserAware { user => f(req)(user) } }

}
