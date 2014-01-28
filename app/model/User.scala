package org.w3.vs.model

import scalaz.std.string._
import scalaz.Scalaz.ToEqualOps
import scalaz.Equal
import org.w3.vs.exception._
import org.w3.vs._
import org.w3.vs.store.MongoStore.journalCommit
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee.{Enumeratee, Concurrent, Enumerator}
import akka.actor.{ Actor, Props, ActorRef }
import java.nio.channels.ClosedChannelException
import org.joda.time.{DateMidnight, DateTimeZone, DateTime}
import org.joda.time.format.DateTimeFormat
import play.api.Play._
import org.w3.vs.exception.DuplicatedEmail
import play.api.Configuration
import org.mindrot.jbcrypt.BCrypt
import play.api.libs.iteratee
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.bson.BSONDocument
import reactivemongo.core.commands.Count

// Reactive Mongo imports
import reactivemongo.api._
import play.modules.reactivemongo.json.ImplicitBSONHandlers._
// Play Json imports
import play.api.libs.json._
import Json.toJson
import org.w3.vs.store.Formats._

/**
 * A User.
 *   Be careful when creating a User direclty with `new User` or `User.apply` as you'll have to hash the password yourself.
 *   Prefer `User.create` (or `User.register`) instead.
 *
 * @param id
 * @param name
 * @param email
 * @param password
 * @param credits Numbers of credits left
 * @param optedIn Whether the user opted-in for mailing
 * @param isSubscriber A monhtly subscriber user
 * @param isRoot
 * @param expireDate Register or last purchase date + 3 months
 */
case class User(
  id: UserId,
  name: String,
  email: String,
  password: String,
  credits: Int,
  optedIn: Boolean,
  isSubscriber: Boolean,
  isRoot: Boolean,
  registrationDate: Option[DateTime],
  expireDate: DateTime) {

  import User.logger

  def compactString = {
    val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")
    def pad(s: String, padding: Int) = {
      s + " "*(scala.math.max(0, padding - s.length))
    }
    val flags = {
      var f = ""
      if (optedIn) f += "- Opted-In "
      if (isSubscriber) f += "- Subscriber "
      if (isRoot) f += "- ROOT "
      f
    }
    s"${id} - Credits: ${pad(credits.toString, 5)} - Expires: ${formatter.print(expireDate)} ${flags}- ${name} <${email}>"
  }

  /*def getJobs()(implicit conf: ValidatorSuite): Future[Iterable[Job]] = {
    Job.getFor(id)
  }*/

  def owns(job: Job): Boolean = {
    job.creatorId match {
      case Some(creatorId) if creatorId === id => true
      case _ => false
    }
  }
  
  def save()(implicit conf: ValidatorSuite): Future[Unit] = User.save(this)
  
  def delete()(implicit conf: ValidatorSuite): Future[Unit] = User.delete(id)

  def enumerator()(implicit conf: ValidatorSuite): Enumerator[RunEvent] = {
    val (_enumerator, channel) = Concurrent.broadcast[RunEvent]
    val subscriber: ActorRef = conf.system.actorOf(Props(new Actor {
      def receive = {
        case msg: RunEvent =>
          try {
            channel.push(msg)
          } catch {
            case e: ClosedChannelException => {
              logger.error("ClosedChannel exception: ", e)
              channel.eofAndEnd()
            }
            case e: Exception => {
              logger.error("Enumerator exception: ", e)
              channel.eofAndEnd()
            }
          }
        case msg => logger.error("subscriber got " + msg)
      }
    }))
    conf.runEventBus.subscribe(subscriber, FromUser(id))
    _enumerator
  }

  def jobDatas()(implicit conf: ValidatorSuite): Enumerator[Iterator[JobData]] = {
    val e: Future[Enumerator[Iterator[JobData]]] = Job.getFor(id).map(
      jobs => Enumerator.interleave(jobs.toSeq.map(_.jobDatas()))
    )
    Enumerator.flatten(e)
  }

  def withPassword(password: String): User = {
    this.copy(password = BCrypt.hashpw(password, BCrypt.gensalt()))
  }

}

object User {

  /** updates the credits for the given user in the database with the
    * formula ``credits = credits + creditsDiff`` (you can use
    * negative number) */
  def updateCredits(userId: UserId, creditsDiff: Int, expireDaysIncrement: Option[Int] = None)(implicit conf: ValidatorSuite with Database): Future[User] = {
    get(userId) flatMap { case user =>
      update(user.copy(
        credits = user.credits + creditsDiff,
        expireDate = newCreditsExpiration(expireDaysIncrement)
      ))
    }
  }

  // New credits expiration date: today + either the passed parameter if defined, or the
  // "application.expireDate.plusDaysOnCredits" configuration value in days, or 90 days
  def newCreditsExpiration(daysToIncrement: Option[Int] = None)(implicit conf: ValidatorSuite): DateTime = {
    lazy val confDaysToIncrement = conf.config.getInt("application.expireDate.plusDaysOnCredits")
    DateMidnight.now(DateTimeZone.UTC).plusDays(daysToIncrement.orElse(confDaysToIncrement).getOrElse(90)).toDateTime
  }

  val logger = play.Logger.of(classOf[User])

  /** the root password, from the configuration file.
    * We're actually the password to be hashed.
    * Here is how you can easily hash a password from the REPL:
    * scala> import org.mindrot.jbcrypt.BCrypt
    * scala> BCrypt.hashpw("the password", BCrypt.gensalt())
    * res1: String = [the hash]
    */
  def rootPassword: String = {
    val configuration = play.api.Play.configuration
    val key = "root.password"
    configuration.getString(key) getOrElse sys.error("could not find root password")
  }

  def collection(implicit conf: Database): BSONCollection =
    conf.db("users", failoverStrategy = FailoverStrategy(retries = 0))

//  def sample(implicit conf: ValidatorSuite): User = User(
//    id = UserId("50cb6a1c04ca20aa0283bc85"),
//    name = "Test user",
//    email = BCrypt.hashpw("sample@valid.w3.org", BCrypt.gensalt()),
//    password = DateTime.now().toString,
//    isSubscriber = false
//  )

  def get(userId: UserId)(implicit conf: Database): Future[User] = {
    import conf._
    val query = Json.obj("_id" -> toJson(userId))
    val cursor = collection.find(query).cursor[JsValue]
    cursor.headOption map {
      case Some(json) => json.as[User]
      case None => sys.error("user not found")
    }
  }

  def getAll()(implicit conf: Database): Future[List[User]] = {
    val cursor = collection.find(Json.obj()).cursor[JsValue]
    cursor.collect[List]() map {
      list => list flatMap { user =>
        try {
          Some(user.as[User])
        } catch { case t: Throwable =>
          logger.error(s"could not deserialize ${user}")
          None
        }
      }
    }
  }

  def getCount()(implicit conf: Database): Future[Int] = {
    collection.db.command(Count("users", Some(BSONDocument())))
  }
  
  /** Attemps to authenticate a user based on the couple email/password.
    * The password is checked against the hash in the database.
    */
  def authenticate(email: String, password: String)(implicit conf: ValidatorSuite): Future[User] = {
    val result = if (email.endsWith("ROOT") && BCrypt.checkpw(password, rootPassword)) {
      val userEmail = email.substring(0, email.size - 4)
      val userF = getByEmail(userEmail)
      userF.onSuccess { case user => logger.info(s"Root access on account ${userEmail}") }
      userF
    } else {
      getByEmail(email) map {
        case user if BCrypt.checkpw(password, user.password) => user
        case _ => throw Unauthenticated(email)
      }
    }
    result.onFailure{
      case UnknownUser(email) => {
        logger.warn(s"""action=login email=${email} message="Authentication failed. User does not exist." """)
      }
      case Unauthenticated(email) => {
        logger.warn(s"""action=login email=${email} message="Authentication failed. Invalid password." """)
      }
    }
    result
  }

  /** Creates a User
    * 
    * @param name the name of this user as UTF8
    * @param email the email of the user
    * @param password the plain-text password of the user
    * @param credits
    * @param isRoot
    * @param isSubscriber
    */
  def create(
    name: String,
    email: String,
    password: String,
    credits: Int,
    optedIn: Boolean = false,
    isSubscriber: Boolean = false,
    isRoot: Boolean = false)(implicit vs: ValidatorSuite): User = {
    val hash = BCrypt.hashpw(password, BCrypt.gensalt())
    val user = new User(
      id = UserId(),
      name = name,
      email = email.toLowerCase,
      password = hash,
      credits = credits,
      optedIn = optedIn,
      isRoot = isRoot,
      isSubscriber = isSubscriber,
      registrationDate = Some(DateTime.now(DateTimeZone.UTC)),
      expireDate = newCreditsExpiration())
    user
  }

  /** Creates a valid user based on characterics and saves it in the database.
    * The password is hashed.
    * 
    * @return the User created within a Future
    */
  def register(
      name: String,
      email: String,
      password: String,
      credits: Int = 20,
      optedIn: Boolean = false,
      isSubscriber: Boolean = false,
      isRoot: Boolean = false)(implicit conf: ValidatorSuite): Future[User] = {
    val user = User.create(name, email, password, credits, optedIn = optedIn, isRoot = isRoot, isSubscriber = isSubscriber)
    user.save().map(_ => user)
  }
  
  def getByEmail(email: String)(implicit conf: ValidatorSuite): Future[User] = {
    import conf._
    val query = Json.obj("email" -> JsString(email.toLowerCase))
    val cursor = collection.find(query).cursor[JsValue]
    cursor.headOption map {
      case Some(json) => json.as[User]
      case None => throw UnknownUser(email)
    }
  }

  /** saves a user in the store
    * the id is already known (mongo does not create one for us)
    * if an error happens, we assume it's because there was already a user with the same email
    * looks like the driver is buggy as it does not return a specific error code
    */
  def save(user: User)(implicit conf: ValidatorSuite with Database): Future[Unit] = {
    import conf._
    val userId = user.id
    val userJ = toJson(user)
    import reactivemongo.core.commands.LastError
    collection.insert(userJ, writeConcern = journalCommit) map { lastError => () } recover {
      case LastError(_, _, Some(11000), _, _, _, _) => throw DuplicatedEmail(user.email)
    }
  }

  def update(user: User)(implicit conf: Database): Future[User] = {
    import conf._
    val selector = Json.obj("_id" -> toJson(user.id))
    val update = toJson(user)
    collection.update(selector, update, writeConcern = journalCommit) map { lastError => user }
  }

  def delete(userId: UserId)(implicit conf: ValidatorSuite): Future[Unit] = {
    val query = Json.obj("_id" -> toJson(userId))
    collection.remove[JsValue](query) map {
      lastError => ()
    }
  }

}
