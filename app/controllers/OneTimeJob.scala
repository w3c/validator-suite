package controllers

import org.w3.vs.controllers._
import org.w3.vs.exception._
import org.w3.vs.model._
import org.w3.vs.view.form._
import play.api.i18n._
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object OneTimeJob extends VSController {

  val logger = play.Logger.of("org.w3.vs.controllers.OneTimeJob")

  def newJob: ActionA = AsyncAction { implicit req =>
    val f: Future[PartialFunction[Format, Result]] =
      for {
        user <- getUser()
      } yield {
        case Html(_) => Ok(views.html.otojForm(OneTimeJobForm.blank, Some(user)))
      }
    f recover {
      case Unauthenticated => {
        case Html(_) => Ok(views.html.otojForm(OneTimeJobForm.blank, None))
      }
    }
  }

  def purchase(): ActionA = AsyncAction { implicit req =>
    val f1: Future[PartialFunction[Format, Result]] = for {
      // Authenticate or register user
      user <- getUser() recoverWith { case Unauthenticated =>
        // If user is not already logged in check the form
        req.body.asFormUrlEncoded.get.get("userType").map(_.headOption).flatten match {
          // New user
          case Some("new") =>
            for {
              form <- Future(RegisterForm.bind() match {
                case Left(form) => throw InvalidFormException(form)
                case Right(validForm) => validForm
              })
              user <- User.register(name = form.name, email = form.email, password = form.password, isSubscriber = false)
            } yield user
          // Known user
          case _ =>
            for {
              form <- Future(LoginForm.bind() match {
                case Left(form) => throw InvalidFormException(form)
                case Right(validForm) => validForm
              })
              user <- User.authenticate(form.email, form.password)
            } yield user
        }
      }
      form <- Future(OneTimeJobForm.bind match {
        case Left(form) => throw InvalidFormException(form)
        case Right(validJobForm) => validJobForm
      })
      job <- form.createJob(user).save()
    } yield {
      case Html(_) => {
        if (user.isSubscriber) {
          Redirect(routes.Jobs.index).withSession("email" -> user.email)
        } else {
          logger.info(s"Redirected user ${user.email} to store for job ${routes.Job.get(job.id).toString}")
          redirectToStore(form.otoj.value, job.id).withSession("email" -> user.email)
        }
      }
    }
    f1 recover {
      case  _: UnauthorizedException => {
        case Html(_) => Unauthorized(views.html.otojForm(OneTimeJobForm.blank.withError("error", Messages("application.invalidCredentials")), None)).withNewSession
        case _ => Unauthorized
      }
      case DuplicatedEmail(email: String) => {
        case Html(_) => BadRequest(views.html.otojForm(OneTimeJobForm.blank.withErrors(List(("error" -> Messages("form.email.duplicate")))), None))
        case _ => BadRequest(Messages("form.email.duplicate"))
      }
      case InvalidFormException(form: VSForm, user) => {
        case Html(_) => {
          form match {
            case form: OneTimeJobForm => BadRequest(views.html.otojForm(form, user))
            case form: RegisterForm => BadRequest(views.html.otojForm(OneTimeJobForm.blank.withErrors(form.errors), user))
            case form: LoginForm => BadRequest(views.html.otojForm(OneTimeJobForm.blank.withErrors(form.errors), user))
          }
        }
        case _ => BadRequest
      }
    }
  }

  def redirectToStore(product: String, jobId :JobId) =
    Redirect("https://sites.fastspring.com/ercim/instant/" + product + "?referrer=" + jobId)

  def callback = Action { req =>
    AsyncResult {
      logger.debug(req.body.asFormUrlEncoded.toString)
      val orderId = req.body.asFormUrlEncoded.get("OrderID").headOption.get // let it fail
      val f: Future[Result] = for {
        jobId <- Future(JobId(req.body.asFormUrlEncoded.get("JobId").headOption.get))
        job <- org.w3.vs.model.Job.get(jobId)
        // TODO: add security check
        _ <- {
          logger.info("Got payment confirmation. Running job " + job.id)
          job.run()
        }
      } yield {
        Ok
      }
      f onFailure { case t: Throwable => logger.error("Error with order " + orderId, t) }
      f
    }
  }

}
