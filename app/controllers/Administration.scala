package controllers

import org.w3.vs.controllers._
import play.api.mvc.WebSocket
import org.w3.vs.{Main, model}
import model.{JobId, CouponId, UserId}
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
import org.w3.vs.exception.{CouponException, InvalidSyntaxCouponException}
import play.api.i18n.Messages

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
        } catch {
          case e: CouponException => Messages(e.getMessage)
          case e: Exception => e.getMessage //toStackTraceString(e)
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
          |    job <jobId>         - job with given id
          |    jobs <regex>        - jobs that match the given regex. Examples:
          |       jobs                   All jobs
          |       jobs \.*               All jobs
          |       jobs Public            Public jobs
          |       jobs 250.*Never        All 250 pages jobs that never started
          |       jobs Anonym.*Never     Anonymous jobs that nerver started
          |       jobs Running           Running jobs
          |    user <userId>       - user with given id
          |    users <regex>       - users that match the given regex. Examples:
          |       users                  All users
          |       users (?i)thomas       Users that include "thomas" case insensitively
          |       users ROOT             Root users
          |       users Opted-In         Users that opted-in for e-mailing
          |    user-create <name> <email> <password> <credits> - register a new user
          |    user-delete <userId>              - delete user with given id
          |    user-add-credits <userId> <nb>    - add nb credits to user with given id
          |    user-set-root <userId>            - set root privileges to user with given id
          |    user-set-password <userId> <pass> - set password of user with given id
          |    admin-add-roots               - add all root users to the current db. Roots are defined in Main.scala.
          |    db-reset                      - reset the database with default data (only available in Dev mode)
          |    db-indexes                    - recreate database indexes
          |    emails                        - comma-separated list of all opted-in emails
          |    coupon <couponId>             - coupon with given id
          |    coupons <regex>               - coupons that match the given regex
          |    coupon-create <code> <campaign> <credits> - creates a new coupon
          |    coupon-create <code> <campaign> <credits> <description> <validityInDays>
          |    coupon-delete <couponId>           - delete coupon with given id
          |    coupon-redeem <couponId> <userId>  - redeem given coupon to given user
          |    coupon-campaign <campaign>         - all coupons of the given campaign
          |    coupon-campaign-create <numberOfCoupons> <prefix> <campaign> <credits> <description> <validityInDays>
          |    coupon-campaign-delete <campaign>  - delete all coupons of the given campaign
          |    coupon-campaign-codes <campaign>   - show coupons codes of the given campaign
          |    """.stripMargin

      case Array("coupons") =>
        val coupons = model.Coupon.getAll().getOrFail()
        displayResults(coupons.map(_.compactString))

      case Array("coupons", regex(reg)) =>
        val coupons = model.Coupon.getAll().getOrFail()
        val filtered = coupons.filter(coupon => reg.findFirstIn(coupon.compactString).isDefined).map(_.compactString)
        displayResults(filtered)

      case Array("coupon", id(couponId)) =>
        model.Coupon.get(CouponId(couponId)).map(_.compactString).recover{case _ => s"No coupon found"}.getOrFail()

      case Array("coupon-create", code, campaign, int(credits)) =>
        model.Coupon.checkSyntax(code)
        val coupon = model.Coupon(code, campaign, credits).save().getOrFail()
        coupon.compactString

      case Array("coupon-create", code, campaign, int(credits), description, int(validityDays)) =>
        model.Coupon.checkSyntax(code)
        val coupon = model.Coupon(code, campaign, credits, description, validityDays).save().getOrFail()
        coupon.compactString

      case Array("coupon-delete", id(couponId)) =>
        model.Coupon.delete(CouponId(couponId)).getOrFail()
        s"coupon ${couponId} deleted"

      case Array("coupon-redeem", id(couponId), id(userId)) =>
        val (_, redeemed) = model.Coupon.redeem(CouponId(couponId), UserId(userId)).getOrFail()
        s"coupon ${couponId} redeemed\n" + redeemed.compactString

      case Array("coupon-campaign-create", int(number), prefix, campaign, int(credits), description, int(validityInDays)) =>
        val pattern = """^\w{2,8}$""".r
        if (!pattern.findFirstIn(prefix).isDefined) {
          s"""invalid prefix: ${prefix}. The prefix must be a string of 2 to 8 alphanumeric characters."""
        } else {
          var coupons = List.empty[String]
          for (i <- 1 to number) { coupons = model.Coupon.generateCode(prefix) +: coupons }
          val c = coupons.map(code => model.Coupon(code, campaign, credits, description, validityInDays)).map(_.save().getOrFail())
          displayResults(c.map(_.compactString))
        }

      case Array("coupon-campaign", campaign) =>
        val coupons = model.Coupon.getCampaign(campaign).getOrFail()
        displayResults(coupons.map(_.compactString))

      case Array("coupon-campaign-codes", campaign) =>
        val coupons = model.Coupon.getCampaign(campaign).getOrFail()
        displayResults(coupons.map(_.code))

      case Array("coupon-campaign-delete", campaign) =>
        val coupons = model.Coupon.getCampaign(campaign).getOrFail()
        coupons.map(coupon => model.Coupon.delete(coupon.id).getOrFail())
        displayResults(coupons.map(_.compactString)) + " deleted"

      case Array("job", id(jobId)) =>
        model.Job.get(JobId(jobId)).map(_.compactString).recover{case _ => s"No job found"}.getOrFail()

      case Array("jobs") =>
        val jobs = model.Job.getAll().getOrFail()
        displayResults(jobs.map(_.compactString))

      case Array("jobs", regex(reg)) =>
        val jobs = model.Job.getAll().getOrFail()
        val filtered = jobs.filter(job => reg.findFirstIn(job.compactString).isDefined).map(_.compactString)
        displayResults(filtered)

      case Array("user", id(userId)) =>
        model.User.get(UserId(userId)).map(_.compactString).recover{case _ => s"No user found"}.getOrFail()

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

      case Array("user-set-root", id(userId)) =>
        org.w3.vs.Main.setRootUser(UserId(userId)).getOrFail()

      case Array("user-set-password", id(userId), password) =>
        import org.mindrot.jbcrypt.BCrypt
        model.User.get(UserId(userId)).flatMap( user =>
          user.isRoot match {
            case true => Future.successful(s"Cannot change the password of a root user: ${user.email} (${user.id}}).")
            case false => model.User.update(user.copy(password = BCrypt.hashpw(password, BCrypt.gensalt()))).map(_ => s"${user.email} (${user.id}}) password changed.")
          }
        ).getOrFail()

      case Array("user-add-credits", id(id), int(credits)) =>
        (for {
          user <- model.User.get(UserId(id))
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
