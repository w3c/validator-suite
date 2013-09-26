package controllers

import org.w3.vs.controllers._
import play.api.mvc.{WebSocket, Result, Action}
import org.w3.vs.exception.UnknownUser
import org.w3.vs.model
import org.w3.vs.model.{User, JobId, UserId}
import play.api.i18n.Messages
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import play.api.cache.Cache
import play.api.Play._
import play.api.libs.iteratee._
import scala.concurrent.duration.Duration
import play.api.libs.json.Json.toJson
import play.api.Mode
import scala.util.matching.Regex
import org.w3.vs.store.Formats._

object Administration extends VSController {

  val logger = play.Logger.of("org.w3.vs.controllers.Administration")

  def index: ActionA = RootAction { implicit req => root =>
    Ok(views.html.admin(root))
  }

  def console: ActionA = RootAction { implicit req => root =>
    Ok(views.html.console(root))
  }

  def jobsPost(): ActionA = RootAction { implicit req => user =>
    // Really don't like that lenghty code to get just a few parameters from the body. Consider a helper function
    /*val (jobId, action) = (for {
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
      SeeOther(routes.Administration.index.url).flashing(
        ("success" -> Messages(msg, jobId + " (" + job.name + ")"))
      )
    }*/ ???
  }

  def usersPost(): ActionA = RootAction { implicit req => root =>
    /*val (email, isSubscriber) = (for {
      body <- req.body.asFormUrlEncoded
      email <- body.get("email").get.headOption
      isSubscriber <- body.get("userType").get.headOption.map {
        _ match {
          case "subscriber" => true
          case _ => false
        }
      }
    } yield (email, isSubscriber)).get

    (for {
      user <- org.w3.vs.model.User.getByEmail(email)
      _ <- {
        Cache.remove(email)
        User.update(user.copy(isSubscriber = isSubscriber))
      }
    } yield {
      SeeOther(routes.Administration.index.url).flashing(
        ("success" -> s"User ${email} succesfully saved with account type subscriber=${isSubscriber}")
      )
    }) recover {
      case UnknownUser(email) => {
        BadRequest(views.html.admin(root = root, messages = List(("error" -> s"Unknown user with email: ${email}"))))
      }
    }*/ ???
  }

  def socket(): WebSocket[String] = WebSocket.using[String] { implicit reqHeader =>
    import org.w3.vs.util.timer._
    assert(getUser().map(_.isRoot).getOrFail() == true)

    def toStackTraceString(ex: Throwable): String = {
      val first = ex.getMessage() + "\n" + ex.getStackTrace().map(s => "\t" + s.toString).mkString("\n")
      ex.getCause() match {
        case cause: Throwable => first + "\n" + "Caused by:\n" + toStackTraceString(cause)
        case _ => first
      }
    }

    val (enum, channel) = Concurrent.broadcast[String]
    def iteratee: Iteratee[String, String] = Cont[String, String] {
      case Input.EOF => Done("end of stream")
      case Input.Empty => iteratee
      case Input.El(command) => {
        val r = try {
          executeCommand(command)
        } catch { case e: Exception =>
          toStackTraceString(e)
        }
        channel.push(r)
        iteratee
      }
    }
    (iteratee, enum)
  }

  // Executes the side-effects of a command, if any, and returns a result as text.
  // The call must stay synchronous.
  def executeCommand(command: String): String = {
    import org.w3.vs.util.timer._
    import play.api.libs.json.Json.prettyPrint

    val int = new Object {
      def unapply(s: String): Option[Int] = try Some(s.toInt) catch { case _: NumberFormatException => None }
    }
    val bool = new Object {
      def unapply(s: String): Option[Boolean] = {
        s match {
          case "true" => Some(true)
          case "false" => Some(false)
          case _ => None
        }
      }
    }
    val regex = new Object {
      def unapply(s: String): Option[Regex] = {
        try { Some(new Regex(s)) }
        catch { case _: Throwable => None }
      }
    }

    val argRegex = new Regex( """"((\\"|[^"])*?)"|[^ ]+""")

    val args = argRegex.findAllMatchIn(command).map {
      r =>
        if (r.group(1) != null) r.group(1)
        else r.group(0)
    }.toArray

    args match {
      case Array("?") | Array("help") =>
        """Available commands:
          |    jobs               - the list of all jobs
          |    jobs <regex>       - the list of jobs that matched the given regex. Examples:
          |       jobs \.*                  All jobs
          |       jobs Public               All public jobs
          |       jobs 250.*Never           All 250 pages jobs that never started
          |       jobs Never.*ANONYM        All anonymous jobs that nerver started
          |    runningJobs        - only the running jobs
          |    user-id <userId>   - informations about the user with given userId
          |    user-email <email> - informations about the user with given email
          |    add-user <name> <email> <password> <isSubscriber> - register a new user
          |    current-users      - users seen in the last 5 minutes (or duration of cache.user.expire)
          |    clear-cache        - clear Play's cache of current users
          |    defaultData        - resets the database with default data (only available in Dev mode)""".stripMargin

      case Array("jobs") =>
        val jobs = model.Job.getAll().getOrFail()
        (s"${jobs.size} results:" :: jobs.map(_.compactString)).mkString("\n")

      case Array("jobs", regex(reg)) =>
        val jobs = model.Job.getAll().getOrFail()
        val filtered = jobs.filter(job => reg.findFirstIn(job.compactString).isDefined).map(_.compactString)
        (s"${filtered.size} results:" :: filtered).mkString("\n")

      case Array("runningJobs") =>
        val jobs = model.Job.getRunningJobs().getOrFail()
        jobs.map(job => s"${job.id} [${job.name}] by ${job.creatorId}").mkString("\n")

      case Array("job", jobId) =>
        val job = model.Job.get(JobId(jobId)).getOrFail()
        job.compactString

      case Array("user-id", userId) =>
        val user = model.User.get(UserId(userId)).getOrFail()
        val json = toJson(user)
        s"Scala: ${user}\nJSON: ${prettyPrint(json)}"

      case Array("user-email", email) =>
        try {
          val user = model.User.getByEmail(email).getOrFail()
          val json = toJson(user)
          s"Scala: ${user}\nJSON: ${prettyPrint(json)}"
        } catch {
          case t: Throwable =>
            t.getMessage
        }

      case Array("add-user", name, email, password, int(credits), bool(isSubscriber)) =>
        try {
          val user = model.User.register(name, email, password, credits, isSubscriber).getOrFail()
          val json = toJson(user)
          s"Scala: ${user}\nJSON: ${prettyPrint(json)}"
        } catch {
          case t: Throwable =>
            t.getMessage
        }

      case Array("current-users") =>
        org.w3.vs.Main.currentUsers().mkString(" | ")

      case Array("clear-cache") =>
        org.w3.vs.Main.clearCache()
        "done"

      case Array("defaultData") if conf.mode == Mode.Dev =>
        org.w3.vs.Main.defaultData()
        "done"

      case _ => s"Command ${command} not found"

    }
  }

}
