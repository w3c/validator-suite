package controllers

import org.w3.vs.controllers._
import play.api.mvc.WebSocket
import org.w3.vs.model
import org.w3.vs.model.{JobId, UserId}
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee._
import play.api.libs.json.Json.toJson
import play.api.Mode
import scala.util.matching.Regex
import org.w3.vs.store.Formats._
import reactivemongo.bson.BSONObjectID

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
      def unapply(s: String): Option[Int] = try Some(s.toInt) catch { case _: Throwable => None }
    }
    val id = new Object {
      def unapply(s: String): Option[BSONObjectID] = {
        try { Some(reactivemongo.bson.BSONObjectID.apply(s)) }
        catch { case _: Throwable => None }
      }
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

    val args = argRegex.findAllMatchIn(command).map {r =>
      if (r.group(1) != null) r.group(1)
      else r.group(0)
    }.toArray

    def displayResults(results: List[String]) = {
      val size = s"${results.size} result(s)"
      if (results.size > 0)
        s"${results.mkString("\n")}\n${size}"
      else
        s"No result"
    }

    args match {
      case Array("?") | Array("help") =>
        """Available commands:
          |    jobs <regex>        - the list of jobs that matched the given regex. Examples:
          |       jobs                   All jobs
          |       jobs \.*               All jobs
          |       jobs <jobId>           The job with given id if any
          |       jobs Public            Public jobs
          |       jobs 250.*Never        All 250 pages jobs that never started
          |       jobs Anonym.*Never     Anonymous jobs that nerver started
          |       jobs Running           Running jobs
          |    users <regex>       - the list of users that matched the given regex. Examples:
          |       users                  All users
          |       users <userId>         The user with given id if any
          |       users (?i)thomas       Users that include "thomas" case insensitively
          |       users ROOT             Root users
          |       users Opted-In         Users that opted-in for e-mailing
          |    db-add-user <name> <email> <password> <credits> - register a new user
          |    db-add-credits <email> <nb> - add nb credits to user with given email
          |    db-set-root <email>         - sets the user with given email as a root
          |    db-add-roots                - adds all root users to the current db. Roots are defined in Main.scala.
          |    db-reset                    - resets the database with default data (only available in Dev mode)""".stripMargin

      case Array("jobs") =>
        val jobs = model.Job.getAll().getOrFail()
        displayResults(jobs.map(_.compactString))

      case Array("jobs", id(jobId)) =>
        val job = model.Job.get(JobId(jobId)).getOrFail()
        job.compactString

      case Array("jobs", regex(reg)) =>
        val jobs = model.Job.getAll().getOrFail()
        val filtered = jobs.filter(job => reg.findFirstIn(job.compactString).isDefined).map(_.compactString)
        displayResults(filtered)

      case Array("users") =>
        val users = model.User.getAll().getOrFail()
        displayResults(users.map(_.compactString))

      case Array("users", id(userId)) =>
        val user = model.User.get(UserId(userId)).getOrFail()
        user.compactString

      case Array("users", regex(reg)) =>
        val users = model.User.getAll().getOrFail()
        val filtered = users.filter(job => reg.findFirstIn(job.compactString).isDefined).map(_.compactString)
        displayResults(filtered)

/*      case Array("runningJobs") =>
        val jobs = model.Job.getRunningJobs().getOrFail()
        jobs.map(job => s"${job.id} [${job.name}] by ${job.creatorId}").mkString("\n")*/

/*      case Array("user-email", email) =>
        try {
          val user = model.User.getByEmail(email).getOrFail()
          val json = toJson(user)
          s"Scala: ${user}\nJSON: ${prettyPrint(json)}"
        } catch {
          case t: Throwable =>
            t.getMessage
        }*/

      case Array("db-add-user", name, email, password, int(credits)) =>
        val user = model.User.register(name, email, password, credits, isSubscriber = false, isRoot = false).getOrFail()
        user.compactString

      case Array("db-reset") if conf.mode == Mode.Dev =>
        org.w3.vs.Main.defaultData()
        "done"

      case Array("db-add-roots") =>
        val roots: Iterable[String] = org.w3.vs.Main.addRootUsers().getOrFail()
        roots.mkString("\n")

      case Array("db-set-root", email) =>
        org.w3.vs.Main.setRootUser(email).getOrFail()

      case Array("db-add-credits", email, int(credits)) =>
        val user = model.User.getByEmail(email).getOrFail()
        model.User.updateCredits(user.id, credits).getOrFail()
        s"${credits} credits added to user ${user.email} (${user.id}})"

      case _ => s"Command ${command} not found"

    }
  }

}
