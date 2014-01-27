package controllers

import org.w3.vs.exception._
import org.w3.vs.controllers._
import org.w3.vs._
import org.w3.vs.model._
import play.Logger.ALogger
import play.api.Play._
import play.api.i18n.Messages
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.JsValue
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.http.MimeTypes
import play.api.mvc.Accepting
import org.w3.vs.exception.UnknownJob
import org.w3.vs.exception.Unauthenticated
import org.w3.vs.view.Forms._

trait VSController extends Controller {

  def logger: ALogger

  implicit val vs: ValidatorSuite with EmailService = org.w3.vs.Global.vs

  implicit val system = vs.system

  def CloseWebsocket = (Iteratee.ignore[JsValue], Enumerator.eof)

  implicit def toAsynchronousResult(result: SimpleResult): Future[SimpleResult] = Future.successful(result)

  def getUser()(implicit reqHeader: RequestHeader): Future[User] = {
    for {
      email <- session.get("email") match {
          case Some(email) => Future.successful(email)
          case _ => Future.failed(Unauthenticated(""))
        }
      user <- model.User.getByEmail(email)
    } yield {
      user
    }
  }

  def getUserOption()(implicit reqHeader: RequestHeader): Future[Option[User]] = {
    getUser.map(Some(_)).recover{case _ => None}
  }

  def Timer(name: String)(f: Future[SimpleResult]): Future[SimpleResult] = {
    if (name == "") {
      f
    } else {
      val timer = Metrics.forController(name)
      val context = timer.time()
      f onComplete { _ => context.stop() }
      f
    }
  }

  def AsyncAction(name: String)(f: Request[AnyContent] => Future[SimpleResult]): Action[AnyContent] = Action.async { implicit req =>
      Timer(name) {
        f(req).recoverWith[SimpleResult] {
          case AccessNotAllowed(msg) => Future.successful(Forbidden(views.html.error._403(msg)))
          case UnknownJob(_) => Global.onHandlerNotFound(req)
        }
      }
  }
  def AsyncAction(f: Request[AnyContent] => Future[SimpleResult]): Action[AnyContent] = AsyncAction("")(f)

  val AcceptsStream = Accepting(MimeTypes.EVENT_STREAM)

  def Authenticated(f: User => Future[SimpleResult])(implicit req: RequestHeader): Future[SimpleResult] = {
    (for {
      user <- getUser()
      result <- f(user)
    } yield result) recover {
      case UnauthorizedException(email) => {
        render {
          case Accepts.Html() =>
            Unauthorized(views.html.login(
              loginForm = LoginForm.fill(Login(email = email, redirectUri = req.uri)),
              registerForm = RegisterForm.fill(Register(redirectUri = req.uri)),
              messages = List(("error", Messages("application.unauthorized")))
            )).withNewSession
          case Accepts.Json() => Unauthorized
        }
      }
    }
  }

  def RootAction(f: Request[AnyContent] => User => Future[SimpleResult]): ActionA = AsyncAction { implicit req =>
    Authenticated { user =>
      user match {
        case user if user.isRoot => f(req)(user)
        case _ => throw AccessNotAllowed()
      }
    }
  }

  def AuthenticatedAction(name: String)(f: Request[AnyContent] => User => Future[SimpleResult]): ActionA =
    AsyncAction(name) { implicit req => Authenticated { user => f(req)(user) } }

  def AuthenticatedAction(f: Request[AnyContent] => User => Future[SimpleResult]): ActionA = AuthenticatedAction("")(f)

  def UserAware(f: Option[User] => Future[SimpleResult])(implicit req: RequestHeader): Future[SimpleResult] = {
    for {
      userO <- getUserOption()
      result <- f(userO)
    } yield result
  }

  def UserAwareAction(name: String)(f: Request[AnyContent] => Option[User] => Future[SimpleResult]): ActionA =
    AsyncAction(name) { implicit req => UserAware { user => f(req)(user) } }

  def UserAwareAction(f: Request[AnyContent] => Option[User] => Future[SimpleResult]): ActionA = UserAwareAction("")(f)

}
