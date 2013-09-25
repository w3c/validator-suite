package controllers

import org.w3.vs.controllers._
import org.w3.vs.exception._
import org.w3.vs.model._
import org.w3.vs.view.form._
import org.w3.vs.util.timer._
import play.api.i18n._
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Purchase extends VSController {

  val logger = play.Logger.of("org.w3.vs.controllers.Purchase")

  def buyJob: ActionA = UserAwareAction { implicit req => user =>
    val messages = user match {
      case Some(user) => List(("info" -> Messages("warning.createOrBuy")))
      case None => List.empty
    }
    Ok(views.html.newOneTimeJob(OneTimeJobForm.blank, user, List.empty))
    /*getUser map {
      case user => Ok(views.html.newOneTimeJob(OneTimeJobForm.blank, user))
    } recover {
      case _: UnauthorizedException =>
        Unauthorized(views.html.register(RegisterForm.redirectTo(req.uri), messages = List(("info", Messages("info.register.first")))))
    } */
  }

  def buyCredits: ActionA = ???

  def buyAction: ActionA = UserAwareAction { implicit req => user =>
    (for {
      form <- Future(OneTimeJobForm.bind match {
        case Left(form) => throw InvalidFormException(form, user)
        case Right(validJobForm) => validJobForm
      })
      job <- form.createJob(user).save()
    } yield {
      render {
        case Accepts.Html() => {
          user match {
            case Some(user) if user.isRoot => {
              job.run().getOrFail()
              Redirect(routes.Jobs.index)
            }
            case _ => {
              logger.info(s"Redirected user ${user.map(_.email)} to store for job ${routes.Job.get(job.id).toString}")
              redirectToStore(form.plan, job.id)
            }
          }
        }
        case Accepts.Json() => ???
      }
    }) recover {
      case InvalidFormException(form: OneTimeJobForm, _) => {
        render {
          case Accepts.Html() => BadRequest(views.html.newOneTimeJob(form, user))
          case Accepts.Json() => BadRequest
        }
      }
    }
  }

  def redirectToStore(product: OneTimePlan, jobId: JobId) =
    Redirect(getStoreUrl(product, jobId))

  def getStoreUrl(product: Plan, jobId: JobId) = {
    "https://sites.fastspring.com/ercim/instant/" + product.fastSpringKey + "?referrer=" + jobId
  }

  def callback = Action {
    req =>
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
        f onFailure {
          case t: Throwable => logger.error("Error with order " + orderId, t)
        }
        f
      }
  }

}
