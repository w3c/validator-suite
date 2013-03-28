package controllers

import org.w3.vs.controllers._
import play.api.mvc.{Result, Action}
import org.w3.vs.exception.UnknownUser
import org.w3.vs.model
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

  def jobsPost(): ActionA = AsyncAction { implicit req =>
    // Really don't like that lenghty code to get just a few parameters from the body. Consider a helper function
    val (jobId, action) = (for {
      body <- req.body.asFormUrlEncoded
      param1 <- body.get("jobId")
      param2 <- body.get("action")
      jobId <- param1.headOption
      action <- param2.headOption
    } yield (jobId, action)).get

    for {
      job <- org.w3.vs.model.Job.get(JobId(jobId))
      msg <- {
        action match {
          case "delete" => job.delete().map(_ => "jobs.deleted")
          case "reset" => job.reset().map(_ => "jobs.reset")
          case "run" => job.run().map(_ => "jobs.run")
        }
      }
    } yield {
      case Html(_) => SeeOther(routes.Administration.index()).flashing(
        ("success" -> Messages(msg, jobId + " (" + job.name + ")"))
      )
    }
  }

  def migration(): ActionA = AsyncAction { implicit req =>
//    val action = (for {
//      body <- req.body.asFormUrlEncoded
//      param <- body.get("action")
//      action <- param.headOption
//    } yield action).get

      import org.w3.util.Util._
    val jobs = List(
"5107f9380bccf4fa0077c888",
"51082ca60bccf42004211958",
"510a6cb40bccf4c033211982",
"510ab43f0bccf4e63d21198c",
"510b9f850bccf4895021199b",
"510fe7250bccf4a20a87475a",
"51117bf90bccf4ea0710c976",
"5113def40bccf4a000f98461",
"5113def40bccf4a000f98461",
"511a41b90bccf44db7bebb7e",
"511c64470bccf4f508b3c4db",
"51219dac0bccf4015db3c517",
"51248da90bccf4170fff79d1",
"511a41b90bccf44db7bebb7e",
"513cd0f50bccf47324ff7afd",
"5140b65c0bccf40648ff7b28",
"5140dd190bccf44b4aff7b2b",
"5142b0c80bccf44782ff7b41").distinct.flatMap { id =>
  try {
   val job = model.Job.get(JobId(id)).getOrFail()
   println("ok for "+User.get(job.creatorId).getOrFail().email)
   Some(job)
  } catch { case e: Exception =>
    println(s"foire avec ${id}")
    None
  }
}

    for {
//      jobs <- model.Job.getAll()
      _ <- Future.sequence(jobs.map(_.run()))
    } yield {
      case Html(_) => SeeOther(routes.Administration.index()).flashing(
        ("success" -> s"${jobs.size} jobs have been successfully rerun")
      )
    }

  }

  def usersPost(): ActionA = AsyncAction { implicit req =>
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
        User.update(user.copy(isSubscriber = isSubscriber))
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
