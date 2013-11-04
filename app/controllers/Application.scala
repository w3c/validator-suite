package controllers

import org.w3.vs.controllers._
import org.w3.vs.exception._
import org.w3.vs.{Metrics, model}
import org.w3.vs.view.form._
import play.api.i18n._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.w3.vs.Emails

object Application extends VSController {
  
  val logger = play.Logger.of("controllers.Application")

  def index: ActionA    = UserAwareAction("front.index")    { implicit req => user => Ok(views.html.index(user)) }
  def faq: ActionA      = UserAwareAction("front.faq")      { implicit req => user => Ok(views.html.faq(user)) }
  def logos: ActionA    = UserAwareAction("front.logos")    { implicit req => user => Ok(views.html.logos(user)) }
  def pricing: ActionA  = UserAwareAction("front.pricing")  { implicit req => user => Ok(views.html.pricing(user)) }
  def features: ActionA = UserAwareAction("front.features") { implicit req => user => Ok(views.html.features(user)) }
  def terms: ActionA    = UserAwareAction("front.terms")    { implicit req => user => Ok(views.html.terms(user)) }
  def privacy: ActionA  = UserAwareAction("front.privacy")  { implicit req => user => Ok(views.html.privacy(user)) }

  def login: ActionA = AsyncAction("front.login") { implicit req =>
    getUser map {
      case _ => Redirect(routes.Jobs.index()) // Already logged in -> redirect to index
    } recover {
      case  _: UnauthorizedException => Ok(views.html.login()).withNewSession
    }
  }

  def tryIt: ActionA = AsyncAction("front.try") { implicit req =>
    getUser map {
      case _ => Redirect(routes.Jobs.index())
    } recover {
      case  _: UnauthorizedException => Redirect(routes.Application.register())
    }
  }

  def register = AsyncAction("front.register") { implicit req =>
    getUser map {
      case _ => Redirect(routes.Jobs.index()) // Already logged in -> redirect to index
    } recover {
      case  _: UnauthorizedException => Ok(views.html.register()).withNewSession
    }
  }

  def loginAction: ActionA = AsyncAction("form.login") { implicit req =>
    (for {
      form <- Future(LoginForm.bind() match {
        case Left(form) => throw InvalidFormException(form)
        case Right(validForm) => validForm
      })
      user <- model.User.authenticate(form.email, form.password) recover { case UnauthorizedException(email) => {
          throw InvalidFormException(form.withGlobalError("application.invalidCredentials"))
        }}
    } yield {
      logger.info(s"id=${user.id} action=login email=${user.email}")
      (form("uri").value match {
        case Some(uri) if (uri != routes.Application.login.url && uri != "") => SeeOther(uri)
        case _ => SeeOther(routes.Jobs.index.url)
      }).withSession("email" -> user.email)
    }) recover {
      case InvalidFormException(form: LoginForm, _) => {
        Metrics.form.loginFailure()
        BadRequest(views.html.login(form)).withNewSession
      }
    }
  }

  def registerAction: ActionA = AsyncAction("form.register") { implicit req =>
    RegisterForm.bind() match {
      case Left(form) => {
        Future.successful {
          BadRequest(views.html.register(registerForm = form))
        }
      }
      case Right(form) => {
        model.User.register(
            name = form.name,
            email = form.email,
            password = form.password,
            optedIn = form.optedIn,
            isSubscriber = false).map {
          case user => {
            logger.info(s"""id=${user.id} action=register email=${user.email} name="${user.name}" opt-in=${user.optedIn}""")
            logger.info(s"""id=${user.id} action=login email=${user.email}""")
            vs.sendEmail(Emails.registered(user))
            val newUri = form("uri").value match {
              case Some(uri) if uri != "" => uri
              case _ => routes.Jobs.index.url
            }
            SeeOther(newUri).withSession("email" -> user.email).flashing(("success", Messages("success.registered.user", user.name, user.email)))
          }
        } recover {
          case DuplicatedEmail(email: String) => {
            logger.info(s"""action=register email=${email} message="Registration failed. Email already in use." """)
            Metrics.form.registerFailure()
            BadRequest(views.html.register(registerForm = form.withError("r_email", "duplicate")))
          }
        }
      }
    }
  }

  def logoutAction: ActionA = UserAwareAction("form.logout") { req => userOpt =>
    userOpt.map(user => logger.info(s"""id=${user.id} action=logout email=${user.email}"""))
    Redirect(routes.Application.index()).withNewSession
  }

}
