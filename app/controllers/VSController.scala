package controllers

import org.w3.vs.exception._
import org.w3.vs.controllers._
import org.w3.vs.Metrics
import org.w3.vs.{Metrics, model, Graphite, Global}
import org.w3.vs.model._
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
      user <- model.User.getByEmail(email)
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
    if (name == "") {
      f
    } else {
      val timer = Metrics.forController(name)
      val context = timer.time()
      f onComplete { _ => context.stop() }
      f
    }
  }

  def AsyncAction(name: String)(f: Request[AnyContent] => Future[Result]): Action[AnyContent] = Action { req =>
    Async {
      Timer(name) {
        f(req) recover {
          case AccessNotAllowed => Global.onHandlerNotFound(req)
          case UnknownJob(_) => Global.onHandlerNotFound(req)
        }
      }
    }
  }
  def AsyncAction(f: Request[AnyContent] => Future[Result]): Action[AnyContent] = AsyncAction("")(f)

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

  def AuthenticatedAction(name: String)(f: Request[AnyContent] => User => Future[Result]): ActionA =
    AsyncAction(name) { implicit req => Authenticated { user => f(req)(user) } }

  def AuthenticatedAction(f: Request[AnyContent] => User => Future[Result]): ActionA = AuthenticatedAction("")(f)

  def UserAware(f: Option[User] => Future[Result])(implicit req: RequestHeader): Future[Result] = {
    for {
      userO <- getUserOption()
      result <- f(userO)
    } yield result
  }

  def UserAwareAction(name: String)(f: Request[AnyContent] => Option[User] => Future[Result]): ActionA =
    AsyncAction(name) { implicit req => UserAware { user => f(req)(user) } }

  def UserAwareAction(f: Request[AnyContent] => Option[User] => Future[Result]): ActionA = UserAwareAction("")(f)

}
