package controllers

import org.w3.vs.controllers._
import org.w3.vs.exception._
import org.w3.vs.{Metrics, model}
import play.api.i18n._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.Emails
import org.w3.vs.view.Forms._
import concurrent.Future

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
      case  _: UnauthorizedException => Ok(views.html.register(RegisterForm, LoginForm)).withNewSession
    }
  }

  def loginAction: ActionA = AsyncAction("form.login") { implicit req =>
    LoginForm.bindFromRequest().fold(
      form => {
        Metrics.form.loginFailure()
        BadRequest(views.html.login(form)).withNewSession
      },
      login => {
        (for {
          user <- model.User.authenticate(login.email, login.password)
        } yield {
          logger.info(s"id=${user.id} action=login email=${user.email}")
          /*val redirectUri = if (login.uri == "" || login.uri == routes.Application.login().url) {
            routes.Jobs.index().url
          } else {
            login.uri
          }*/
          SeeOther(login.redirectUri  ).withSession("email" -> user.email)
        }) recover {
          case UnauthorizedException(email) => {
            Metrics.form.loginFailure()
            val failForm = LoginForm.bindFromRequest().withGlobalError("application.invalidCredentials", routes.PasswordReset.resetRequest().url)
            BadRequest(views.html.login(failForm)).withNewSession
          }
        }
      }
    )
  }

  def registerAction: ActionA = AsyncAction("form.register") { implicit req =>
    RegisterForm.bindFromRequest().fold(
      form => BadRequest(views.html.register(registerForm = form, loginForm = LoginForm)),
      register => (for {
        couponOpt <- register.coupon.map{ coupon =>
          model.Coupon.getIfValid(coupon).map(Some(_))
        }.getOrElse(Future.successful(None))
        user <- model.User.register(
          name = register.name,
          email = register.email,
          password = register.password,
          optedIn = register.optedIn,
          isSubscriber = false)
        _ <- register.coupon.map{ coupon =>
          model.Coupon.redeem(coupon, user.id)
        }.getOrElse(Future.successful())
      } yield {
        logger.info(s"""id=${user.id} action=register email=${user.email} name="${user.name}" opt-in=${user.optedIn}""")
        logger.info(s"""id=${user.id} action=login email=${user.email}""")
        vs.sendEmail(Emails.registered(user))
        val msg = couponOpt match {
          case Some(coupon) => Messages("success.registered.user", user.name, user.email) + "<br/>" + Messages("user.coupon.redeemed", coupon.description.getOrElse("Validator Suite"), coupon.code, coupon.credits)
          case _ => Messages("success.registered.user", user.name, user.email)
        }
        SeeOther(register.redirectUri).withSession("email" -> user.email).flashing("success" -> msg)
      }) recover {
        case DuplicatedEmail(email: String) => {
          logger.info(s"""action=register email=${email} message="Registration failed. Email already in use." """)
          Metrics.form.registerFailure()
          BadRequest(views.html.register(
            registerForm = RegisterForm.bindFromRequest(),
            loginForm = LoginForm,
            messages = List("error" -> Messages("r_email.error.duplicate", routes.Application.login().url, routes.PasswordReset.resetRequest().url)))
          )
        }
        case CouponException(code, msg) => {
          logger.info(s"""action=register email=??? message="Registration failed. Invalid coupon: ${code} - ${msg}." """)
          Metrics.form.registerFailure()
          BadRequest(views.html.register(
            registerForm = RegisterForm.bindFromRequest().withError("coupon", msg),
            loginForm = LoginForm,
            messages = List.empty)
          )
        }
      }
    )
  }

  def logoutAction: ActionA = UserAwareAction("form.logout") { req => userOpt =>
    userOpt.map(user => logger.info(s"""id=${user.id} action=logout email=${user.email}"""))
    Redirect(routes.Application.index()).withNewSession
  }

}
