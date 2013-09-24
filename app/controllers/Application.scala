package controllers

import org.w3.vs.controllers._
import org.w3.vs.exception._
import org.w3.vs.model._
import org.w3.vs.view.form._
import play.api.i18n._
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.w3.vs.util.timer._
import com.codahale.metrics._
import org.w3.vs.Graphite

object Application extends VSController {
  
  val logger = play.Logger.of("org.w3.vs.controllers.Application")

  def faq: ActionA     = UserAwareAction { implicit req => user => Ok(views.html.faq(user)) }
  def index: ActionA   = UserAwareAction { implicit req => user => Ok(views.html.index(user)) }
  def pricing: ActionA = UserAwareAction { implicit req => user => Ok(views.html.pricing(user)) }
  def logos: ActionA   = UserAwareAction { implicit req => user => Ok(views.html.logos(user)) }
  def terms: ActionA   = UserAwareAction { implicit req => user => Ok(views.html.terms(user)) }
  def privacy: ActionA = UserAwareAction { implicit req => user => Ok(views.html.privacy(user)) }

  val loginName = (new controllers.javascript.ReverseApplication).login.name
  val loginTimer = Graphite.metrics.timer(MetricRegistry.name(Application.getClass, loginName))

  def login: ActionA = AsyncAction { implicit req =>
    val f = getUser map {
      case _ => Redirect(routes.Jobs.index()) // Already logged in -> redirect to index
    } recover {
      case  _: UnauthorizedException => Ok(views.html.login()).withNewSession
    }
    f.timer(loginName).timer(loginTimer)
  }

  def register = AsyncAction { implicit req =>
    val f = getUser map {
      case _ => Redirect(routes.Jobs.index()) // Already logged in -> redirect to index
    } recover {
      case  _: UnauthorizedException => Ok(views.html.register()).withNewSession
    }
    f.timer(loginName).timer(loginTimer)
  }

  def logout: ActionA = Action {
    Redirect(routes.Application.index()).withNewSession // .flashing("success" -> Messages("application.loggedOut"))
  }

  val authenticateName = (new controllers.javascript.ReverseApplication).loginAction.name
  val authenticateTimer = Graphite.metrics.timer(MetricRegistry.name(Application.getClass, authenticateName))
  
  def loginAction: ActionA = AsyncAction { implicit req =>
    val f = (for {
      form <- Future(LoginForm.bind() match {
        case Left(form) => throw InvalidFormException(form)
        case Right(validForm) => validForm
      })
      user <- User.authenticate(form.email, form.password) recover {
        case UnauthorizedException(email) => throw InvalidFormException(form.withGlobalError("application.invalidCredentials"))
      }
    } yield {
      (form("uri").value match {
        case Some(uri) if (uri != routes.Application.login.url && uri != "") => SeeOther(uri)
        case _ => SeeOther(routes.Jobs.index.url)
      }).withSession("email" -> user.email)

      /*(for {
        body <- req.body.asFormUrlEncoded
        param <- body.get("uri")
        uri <- param.headOption
      } yield uri) match {
        case Some(uri) if (uri != routes.Application.login.url && uri != "") => {
          SeeOther(uri).withSession("email" -> user.email)
        } // Redirect to "uri" param if specified
        case _ => SeeOther(routes.Jobs.index).withSession("email" -> user.email)
      }*/
    }) recover {
      /*case UnauthorizedException(email) =>
        Unauthorized(views.html.login(
          loginForm = LoginForm(email).withGlobalError("application.invalidCredentials")
        )).withNewSession*/
      case InvalidFormException(form: LoginForm, _) => {
        BadRequest(views.html.login(form)).withNewSession
      }

    }
    f.timer(authenticateName).timer(authenticateTimer)
  }

  val optInLogger = play.Logger.of("OptInUsers")

  def registerAction: ActionA = AsyncAction { implicit req =>
    RegisterForm.bind() match {
      case Left(form) => {
        Future.successful(BadRequest(views.html.register(registerForm = form)))
      }
      case Right(form) => {
        val f = User.register(name = form.name, email = form.email, password = form.password, isSubscriber = false).map {
          case user => {
            getFormParam("optin") match {
              case Some("on") => optInLogger.info(s"${user.name} <${user.email}>")
              case _ =>
            }
            (getFormParam("uri") match {
              case Some(uri) if uri != "" => SeeOther(uri)
              case _ => SeeOther(routes.Jobs.index.url)
            }).withSession("email" -> user.email).flashing(("success", Messages("success.registered.user", user.name, user.email)))
          }
        } recover {
          case DuplicatedEmail(email: String) => BadRequest(views.html.register(registerForm = form.withError("r_email", "duplicate")))
        }
        f
      }
    }
  }

  private def getFormParam(param: String)(implicit req: Request[AnyContent]) = for {
    body <- req.body.asFormUrlEncoded
    paramL <- body.get(param)
    param <- paramL.headOption
  } yield param
  
}
