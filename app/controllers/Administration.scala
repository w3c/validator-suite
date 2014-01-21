package controllers

import org.w3.vs.controllers._
import play.api.mvc.WebSocket
import org.w3.vs.{Main, model}
import model.{CouponId, JobId, UserId}
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee._
import play.api.libs.json.Json.toJson
import play.api.Mode
import scala.util.matching.Regex
import org.w3.vs.store.Formats._
import reactivemongo.bson.BSONObjectID
import scala.concurrent.Future
import concurrent.duration.FiniteDuration
import org.w3.vs.store.MongoStore

object Administration extends VSController {

  val logger = play.Logger.of("controllers.Administration")

  def console: ActionA = RootAction { implicit req => root =>
    Ok(views.html.console(root))
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
    val ping = new Runnable {def run() { channel.push("ping")} }
    val pingEvents = vs.system.scheduler.schedule(FiniteDuration(30, "s"), FiniteDuration(30, "s"), ping)
    def iteratee: Iteratee[String, String] = Cont[String, String] {
      case Input.EOF => {
        pingEvents.cancel()
        channel.eofAndEnd()
        Done("end of stream")
      }
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

    // TODO add caching

    args match {
      case Array("?") | Array("help") =>
        """Available commands:
          |    job <jobId>        - the job with given id if any
          |    jobs <regex>        - the list of jobs that matched the given regex. Examples:
          |       jobs                   All jobs
          |       jobs \.*               All jobs
          |       jobs Public            Public jobs
          |       jobs 250.*Never        All 250 pages jobs that never started
          |       jobs Anonym.*Never     Anonymous jobs that nerver started
          |       jobs Running           Running jobs
          |    user <userId>       - the user with given id if any
          |    users <regex>       - the list of users that matched the given regex. Examples:
          |       users                  All users
          |       users (?i)thomas       Users that include "thomas" case insensitively
          |       users ROOT             Root users
          |       users Opted-In         Users that opted-in for e-mailing
          |    user-create <name> <email> <password> <credits> - register a new user
          |    user-delete <userId>          - delete user with given userId
          |    user-add-credits <email> <nb> - add nb credits to user with given email
          |    user-set-root <email>         - sets the user with given email as a root
          |    user-set-password <email> <pass> - changes a user password
          |    admin-add-roots               - adds all root users to the current db. Roots are defined in Main.scala.
          |    db-reset                      - resets the database with default data (only available in Dev mode)
          |    db-indexes                    - recreate database indexes
          |    emails                        - comma-separated list of all opted-in emails
          |    coupons [regex]
          |    coupon [code]
          |    coupon-create [code] [campaign] [credits]
          |    coupon-create [code] [campaign] [credits] [description] [validityInDays]
          |    coupon-delete [id]
          |    coupon-delete [code]
          |    coupon-redeem [code] [userId]
          |    """.stripMargin

      case Array("coupons") =>
        val coupons = model.Coupon.getAll().getOrFail()
        displayResults(coupons.map(_.compactString))

      case Array("coupons", regex(reg)) =>
        val coupons = model.Coupon.getAll().getOrFail()
        val filtered = coupons.filter(coupon => reg.findFirstIn(coupon.compactString).isDefined).map(_.compactString)
        displayResults(filtered)

      case Array("coupon", code) =>
        val coupon = model.Coupon.get(code).getOrFail()
        coupon.compactString

      case Array("coupon-create", code, campaign, int(credits)) =>
        val coupon = model.Coupon(code, campaign, credits).save().getOrFail()
        coupon.compactString

      case Array("coupon-create", code, campaign, int(credits), description, int(validityDays)) =>
        val coupon = model.Coupon(code, campaign, credits, description, validityDays).save().getOrFail()
        coupon.compactString

      case Array("coupon-delete", id(id)) =>
        model.Coupon.delete(CouponId(id)).getOrFail()
        s"coupon ${id} deleted"

      case Array("coupon-delete", code) =>
        model.Coupon.delete(code).getOrFail()
        s"coupon ${code} deleted"

      case Array("coupon-redeem", code, id(userId)) =>
        val (user, redeemed) = model.Coupon.redeem(code, UserId(userId)).getOrFail()
        s"coupon ${code} redeemed\n" + redeemed.compactString

      case Array("job", id(jobId)) =>
        val job = model.Job.get(JobId(jobId)).getOrFail()
        job.compactString

      case Array("jobs") =>
        val jobs = model.Job.getAll().getOrFail()
        displayResults(jobs.map(_.compactString))

      case Array("jobs", regex(reg)) =>
        val jobs = model.Job.getAll().getOrFail()
        val filtered = jobs.filter(job => reg.findFirstIn(job.compactString).isDefined).map(_.compactString)
        displayResults(filtered)

      case Array("user", id(userId)) =>
        val user = model.User.get(UserId(userId)).getOrFail()
        user.compactString

      case Array("users") =>
        val users = model.User.getAll().getOrFail()
        displayResults(users.map(_.compactString))

      case Array("users", regex(reg)) =>
        val users = model.User.getAll().getOrFail()
        val filtered = users.filter(job => reg.findFirstIn(job.compactString).isDefined).map(_.compactString)
        displayResults(filtered)

      case Array("emails") =>
        val optIns = model.User.getAll().getOrFail().filter(_.optedIn == true)
        optIns.map(user => s"${user.name}\t${user.email}").mkString("", "\n", s"\n${optIns.size} result(s).")

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

      case Array("user-create", name, email, password, int(credits)) =>
        val user = model.User.register(name, email, password, credits, isSubscriber = false, isRoot = false).getOrFail()
        user.compactString

      case Array("db-reset") if vs.mode == Mode.Dev =>
        MongoStore.reInitializeDb().getOrFail()
        Main.defaultData()
        "done"

      case Array("db-indexes") =>
        MongoStore.createIndexes().getOrFail()
        s"indexes created"

      case Array("admin-add-roots") =>
        val roots: Iterable[String] = org.w3.vs.Main.addRootUsers().getOrFail()
        roots.mkString("\n")

      case Array("user-set-root", email) =>
        org.w3.vs.Main.setRootUser(email).getOrFail()

      case Array("user-set-password", email, password) =>
        import org.mindrot.jbcrypt.BCrypt
        model.User.getByEmail(email).flatMap( user =>
          user.isRoot match {
            case true => Future.successful(s"Cannot change the password of a root user: ${email}.")
            case false => model.User.update(user.copy(password = BCrypt.hashpw(password, BCrypt.gensalt()))).map(_ => s"${email} password changed.")
          }
        ).getOrFail()

      case Array("user-add-credits", email, int(credits)) =>
        (for {
          user <- model.User.getByEmail(email)
          saved <- model.User.updateCredits(user.id, credits)
        } yield {
          // TODO adminId
          Purchase.logger.info(s"""id=${user.id} action=credits-update amount=${credits} expiration-date="${saved.expireDate.toString("yyyy-MM-dd")}" message="credits updated by some admin" """)
          s"${credits} credits added to user ${user.email} (${user.id}})"
        }).getOrFail()

      case Array("user-delete", id(userId)) =>
        model.User.delete(UserId(userId)).getOrFail()
        s"user ${userId} deleted"

      case _ => s"Command ${command} not found"

    }
  }

}
