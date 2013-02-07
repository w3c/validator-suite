package controllers

import org.w3.vs.controllers._
import play.api.mvc.{Action, AsyncResult}
import org.w3.vs.exception.UnauthorizedException
import org.w3.vs.view.form.LoginForm
import org.w3.vs.model.JobId
import play.api.i18n.Messages
import scala.concurrent.ExecutionContext.Implicits.global

object Administration extends VSController {

  val logger = play.Logger.of("org.w3.vs.controllers.Administration")

  def jobs: ActionA = Action { implicit req =>
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
      case Html(_) => SeeOther(routes.Administration.jobs()).flashing(("success" -> Messages("jobs.deleted", jobId + " (" + job.name + ")")))
    }
  }

}
