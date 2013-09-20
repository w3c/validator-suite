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

  def buy: ActionA = Action { implicit req =>
    AsyncResult {
      getUser map {
        case user => Ok(views.html.newJob(OneTimeJobForm.blank, user))
      } recover {
        case  _: UnauthorizedException =>
          Unauthorized(views.html.register(RegisterForm.redirectTo(req.uri), messages = List(("info", Messages("info.register.first")))))
      } recover toError
    }
  }

  def buyAction(): ActionA = AuthAsyncAction { implicit req => user =>
    val f1: Future[PartialFunction[Format, Result]] = for {
      form <- Future(OneTimeJobForm.bind match {
        case Left(form) => throw InvalidFormException(form, Some(user))
        case Right(validJobForm) => validJobForm
      })
      job <- form.createJob(user).save()
    } yield {
      case Html(_) => {
        if (user.isSubscriber) {
          Redirect(routes.Jobs.index).withSession("email" -> user.email)
        } else {
          logger.info(s"Redirected user ${user.email} to store for job ${routes.Job.get(job.id).toString}")
          redirectToStore(form.plan, job.id).withSession("email" -> user.email)
        }
      }
    }
    f1 recover {
      case InvalidFormException(form: OneTimeJobForm, _) => {
        case Html(_) => BadRequest(views.html.newJob(form, user))
        case _ => BadRequest
      }
    }
  }

  def redirectToStore(product: OneTimePlan, jobId :JobId) =
    Redirect("https://sites.fastspring.com/ercim/instant/" + product.fastSpringKey + "?referrer=" + jobId)

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
          // TODO: check if the order size matches the job's!
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
