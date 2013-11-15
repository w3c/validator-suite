package org.w3.vs.view

import controllers.routes
import java.util.concurrent.TimeUnit
import org.w3.vs.Global
import org.w3.vs.Global._
import org.w3.vs.model.{Strategy, Job, User}
import org.w3.vs.web.URL
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.data.validation.Constraints._
import scala.concurrent.{ExecutionContext, Await}
import scala.concurrent.duration.Duration
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

  def PasswordForm(user: User): Form[Password] = Form(
    mapping(
      "p_current" -> nonEmptyText,
      "p_new" -> nonEmptyText(minLength = 6),
      "p_new2" -> nonEmptyText(minLength = 6)
    )(Password.apply)(Password.unapply)
      .verifying("password.dont_match", p => p.newPassword == p.newPassword2)
      .verifying("application.invalidPassword", {p =>
        implicit val c = Global.vs
        Await.result(User.authenticate(user.email, p.current).map(_ => true).recover{case _ => false}, Duration("3s"))
      })
  )

  /**
   * Register
   */
  case class Register(
    name: String = "",
    email: String = "",
    password: String = "",
    password2: String = "",
    optedIn: Boolean = false,
    redirectUri: String = routes.Jobs.index().url)

  val RegisterForm : Form[Register] = Form(
    mapping(
      "userName" -> nonEmptyText,
      "r_email" -> email,
      "r_password" -> nonEmptyText(minLength = 6),
      "repeatPassword" -> text,
      "optedIn" -> of[Boolean](checkboxFormatter),
      "uri" -> text
    )(Register.apply)(Register.unapply)
      .verifying("password.dont_match", p => p.password == p.password2)
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
      "u_optedIn" -> of[Boolean](checkboxFormatter)
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
   * Job
   */
  def JobForm(user: User): Form[Job] = Form(
    mapping(
      "name" -> nonEmptyText,
      "entrypoint" -> of[URL].verifying("invalid", { url =>
        try {
          // careful: this is blocking IO, potentially up to 10 seconds
          val code = vs.formHttpClient.prepareGet(url.toString).execute().get(10, TimeUnit.SECONDS).getStatusCode
          code == 200
        } catch { case e: Exception =>
          false
        }
      }),
      "maxPages" -> of[Int].verifying(min(1)).verifying("creditMaxExceeded", { credits =>
        credits <= user.credits
      })
    )((name, entrypoint, maxPages) => {
      val strategy = Strategy(URL(entrypoint), maxPages)
      Job(name = name, strategy = strategy, creatorId = Some(user.id))
    })((job: Job) =>
      Some(job.name, job.strategy.entrypoint, job.strategy.maxResources)
    )
  )

}
