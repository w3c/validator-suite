package org.w3.vs.view

import controllers.routes
import java.util.concurrent.TimeUnit
import org.w3.vs.Global._
import org.w3.vs.model._
import org.w3.vs.web.URL
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.data.validation.Constraints._
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global

object Forms {

  /**
   * Login
   */
  case class Login(
    email: String = "",
    password: String = "",
    redirectUri: String = routes.Jobs.index().url)

  val LoginForm = Form(mapping(
    "l_email" -> email.verifying(nonEmpty),
    "l_password" -> nonEmptyText,
    "uri" -> text
  )(Login.apply)(Login.unapply)).fill(Login())

  /**
   * Password
   */
  case class Password(
    current: String = "",
    newPassword: String = "",
    newPassword2: String = "")

  def PasswordForm: Form[Password] = Form(
    mapping(
      "p_current" -> nonEmptyText,
      "p_new" -> nonEmptyText(minLength = 6),
      "p_new2" -> nonEmptyText(minLength = 6)
    )(Password.apply)(Password.unapply)
      .verifying("r_new2.error.mismatch", p => p.newPassword == p.newPassword2)
  )

  /**
   * Register
   */
  case class Register(
    name: String = "",
    email: String = "",
    password: String = "",
    password2: String = "",
    coupon: Option[String] = None,
    optedIn: Boolean = false,
    redirectUri: String = routes.Jobs.index().url)

  val RegisterForm : Form[Register] = Form(
    mapping(
      "userName" -> nonEmptyText,
      "r_email" -> email,
      "r_password" -> nonEmptyText(minLength = 6),
      "r_password2" -> text,
      "coupon" -> optional(text).verifying("error.syntax", { couponOpt => !couponOpt.isDefined || Coupon.pattern.findFirstIn(couponOpt.get).isDefined }),
      "optedIn" -> of[Boolean],
      "uri" -> text
    )(Register.apply)(Register.unapply)
      .verifying("r_password2.error.mismatch", p => p.password == p.password2)
  ).fill(Register())

  /**
   * Account
   */
  case class Account(
    name: String,
    email: String,
    optedIn: Boolean) {

    def update(user: User): User = {
      user.copy(
        name = name,
        email = email,
        optedIn = optedIn
      )
    }
  }

  val AccountForm: Form[Account] = Form(
    mapping(
      "u_userName" -> nonEmptyText,
      "u_email" -> email,
      "u_optedIn" -> of[Boolean]
    )(Account.apply)(Account.unapply)
  )

  def AccountForm(user: User): Form[Account] = AccountForm.fill(
    Account(
      name = user.name,
      email = user.email,
      optedIn = user.optedIn
    )
  )

  /**
   * Coupon
   */
  val CouponForm: Form[String] = Form(
    single(
      "coupon" -> nonEmptyText.verifying("error.syntax", Coupon.pattern.findFirstIn(_).isDefined)
    )
  )

  /**
   * Job
   */
  def JobForm(user: User): Form[Job] = Form(
    mapping(
      "name" -> nonEmptyText,
      "entrypoint" -> of[URL],
      "maxPages" -> of[Int].verifying(min(1), max(2000)).verifying("creditMaxExceeded", { credits =>
        credits <= user.credits
      })
    )((name, entrypoint, maxPages) => {
      val strategy = Strategy(URL(entrypoint), maxPages)
      Job(name = name, strategy = strategy, creatorId = Some(user.id))
    })((job: Job) =>
      Some(job.name, job.strategy.entrypoint, job.strategy.maxResources)
    )
  )

  /**
   * Reset Password
   */
  val ResetRequestForm: Form[String] = Form(single("reset_email" -> email))

  case class Reset(email: String, password: String, password2: String)

  val ResetForm: Form[Reset] = Form(
    mapping(
      "reset_email" -> email,
      "reset_password" -> nonEmptyText(minLength = 6),
      "reset_password2" -> text
    )(Reset.apply)(Reset.unapply).verifying("reset_password2.error.mismatch", p => p.password == p.password2)
  )

}
