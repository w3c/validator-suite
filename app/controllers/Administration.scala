package controllers

import org.w3.vs.controllers._
import play.api.mvc.{WebSocket, Result, Results, Action}
import org.w3.vs.exception.UnknownUser
import org.w3.vs.model
import org.w3.vs.model.{User, JobId}
import play.api.i18n.Messages
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import play.api.cache.Cache
import play.api.Play._
import play.api.libs.iteratee._
import scala.concurrent.duration.Duration

object Administration extends VSController {

  val logger = play.Logger.of("org.w3.vs.controllers.Administration")

  def index: ActionA = RootBasicAuth { implicit req =>
    Ok(views.html.admin())
  }

  def e503(): ActionA = Action {
    ServiceUnavailable(views.html.error._503())
  }

  def console: ActionA = RootBasicAuth { implicit req =>
    Ok(views.html.console())
  }

  def jobsPost(): ActionA = RootBasicAuth { implicit req =>
    // Really don't like that lenghty code to get just a few parameters from the body. Consider a helper function
    val (jobId, action) = (for {
      body <- req.body.asFormUrlEncoded
      param1 <- body.get("jobId")
      param2 <- body.get("action")
      jobId <- param1.headOption
      action <- param2.headOption
    } yield (jobId, action)).get

    val f: Future[Result] = for {
      job <- org.w3.vs.model.Job.get(JobId(jobId))
      msg <- {
        action match {
          case "delete" => job.delete().map(_ => "jobs.deleted")
          case "reset" => job.reset().map(_ => "jobs.reset")
          case "run" => job.run().map(_ => "jobs.run")
        }
      }
    } yield {
      SeeOther(routes.Administration.index()).flashing(
        ("success" -> Messages(msg, jobId + " (" + job.name + ")"))
      )
    }

    Await.result(f.recover(toError), Duration(5, "s"))
  }

  def migration(): ActionA = RootBasicAuth { implicit req =>
    Ok("")
  }

  def usersPost(): ActionA = RootBasicAuth { implicit req =>
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

    val f: Future[Result] = for {
      user <- org.w3.vs.model.User.getByEmail(email)
      _ <- {
        Cache.remove(email)
        User.update(user.copy(isSubscriber = isSubscriber))
      }
    } yield {
      SeeOther(routes.Administration.index()).flashing(
        ("success" -> s"User ${email} succesfully saved with account type subscriber=${isSubscriber}")
      )
    }

    f recover {
      case UnknownUser(email) => {
        BadRequest(views.html.admin(messages = List(("error" -> s"Unknown user with email: ${email}"))))
      }
    }

    Await.result(f.recover(toError), Duration(5, "s"))
  }

  def socket(): WebSocket[String] = WebSocket.using[String] { implicit reqHeader =>

    // TODO: Find a way to authenticate. (Authentication header is not passed in a websocket request)

    // TODO: Implement this method in a new class
    // Executes the side-effects of a command, if any, and returns a result as text.
    // The call must stay synchronous. Below implementation is for example only.
    def executeCommand(command: String): String = {
      import org.w3.vs.util.timer._
      command match {
        case _ @ ("?" | "help") =>
          """Basic UI functionality:
            |    Ctrl+l to clear the console
            |    Up/Down arrow to move through command history
            |
            |Commands implemented for illustration purposes:
            |    jobs     - displays the list of all jobs
            |    long     - a command that takes a long time to perform
            |
            |Note: The console is locked when a command is being processed by the server (i.e. the server *must* return a result for every command, may that be the empty string.)""".stripMargin
        case "jobs" => {
          val jobs = model.Job.getAll().getOrFail()
          jobs.map(job => s"${job.name} - ${job.id}").mkString("\n")
        }
        case "long" => {
          Thread.sleep(3000)
          "done"
        }
        case s => s"Command ${s} not found"
      }
    }

    val (enum, channel) = Concurrent.broadcast[String]
    def iteratee: Iteratee[String, String] = Cont[String, String]{
      case Input.EOF => Done("end of stream")
      case Input.Empty => iteratee
      case Input.El(command) => {
        channel.push(executeCommand(command))
        iteratee
      }
    }
    (iteratee, enum)
  }

}
