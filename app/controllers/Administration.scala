package controllers

import org.w3.vs.controllers._
import play.api.mvc.{Result, Action}
import org.w3.vs.exception.UnknownUser
import org.w3.vs.model.{User, JobId}
import play.api.i18n.Messages
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.cache.Cache
import play.api.Play._

object Administration extends VSController {

  val logger = play.Logger.of("org.w3.vs.controllers.Administration")

  def index: ActionA = Action { implicit req =>
    Ok(views.html.admin())
  }

  def jobsPost: ActionA = AsyncAction { implicit req =>
    val jobId = (for {
      body <- req.body.asFormUrlEncoded
      param <- body.get("jobId")
      jobId <- param.headOption
    } yield jobId).get

    for {
      job <- org.w3.vs.model.Job.get(JobId(jobId))
      _ <- job.delete() // there's only a delete action for now
    } yield {
      case Html(_) => SeeOther(routes.Administration.index()).flashing(("success" -> Messages("jobs.deleted", jobId + " (" + job.name + ")")))
    }
  }

  def usersPost: ActionA = AsyncAction { implicit req =>
    val (email, isSubscriber) = (for {
      body <- req.body.asFormUrlEncoded
      email <- body.get("email").get.headOption
      isSubscriber <- body.get("userType").get.headOption.map {
        _ match {
          case "subscriber" => true
          case _ => false
        }
      }
    } yield (email, isSubscriber)).get

    val f: Future[PartialFunction[Format, Result]] = for {
      user <- org.w3.vs.model.User.getByEmail(email)
      _ <- {
        Cache.remove(email)
        User.update(user.copy(vo = user.vo.copy(isSubscriber = isSubscriber)))
      }
    } yield {
      case Html(_) => SeeOther(routes.Administration.index()).flashing(
        ("success" -> s"User ${email} succesfully saved with account type subscriber=${isSubscriber}")
      )
    }

    f recover {
      case UnknownUser => {
        case Html(_) => BadRequest(views.html.admin(List(("error" -> s"Unknown user with email: ${email}"))))
      }
    }
  }

}
