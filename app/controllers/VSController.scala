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
import org.w3.vs.Global
import play.api.http.{MimeTypes, MediaRange}

trait VSController extends Controller {

  def logger: ALogger

  implicit val conf = org.w3.vs.Global.conf

  implicit val system = conf.system

  def CloseWebsocket = (Iteratee.ignore[JsValue], Enumerator.eof)

  implicit def toAsynchronousResult(result: Result): Future[Result] = Future.successful(result)

  def getUser()(implicit reqHeader: RequestHeader): Future[User] = {
    for {
      // TODO sort out this code
      email <- Future(session.get("email").get) recoverWith { case _ => Future(throw Unauthenticated("")) }
      user <- Future(Cache.getAs[User](email).get) recoverWith { case _ => User.getByEmail(email) }
    } yield {
      Cache.set(email, user, current.configuration.getInt("cache.user.expire").getOrElse(300))
      user
    }
  }

  def getUserOption()(implicit reqHeader: RequestHeader): Future[Option[User]] = {
    getUser.map(Some(_)).recover{case _ => None}
  }

  def AsyncAction(f: Request[AnyContent] => Future[Result]) = Action { req => Async(f(req)) }

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

  def RootBasicAuth(f: Request[AnyContent] => Result): ActionA = Action { implicit req =>
    val action =
      req.headers.get("Authorization").flatMap { headerValue =>
        val b64 = headerValue.replace("Basic ", "")
        new String(decodeBase64(b64.getBytes)).split(":") match {
          case Array("ROOT", rootPassword) if BCrypt.checkpw(rootPassword, User.rootPassword) =>
            Some(f(req))
          case _ => None
        }
      }
    action.getOrElse(Unauthorized("unauthorized").withHeaders(("WWW-Authenticate", """Basic realm="W3C Validator Suite"""")))
  }

}
