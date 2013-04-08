package org.w3.vs.model

import scalaz.std.string._
import scalaz.Scalaz.ToEqualOps
import org.w3.vs.exception._
import org.w3.vs._
import org.w3.vs.util.Util.journalCommit
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee.{Enumeratee, Concurrent, Enumerator}
import akka.actor.{ Actor, Props, ActorRef }
import java.nio.channels.ClosedChannelException
import org.joda.time.DateTime
import play.api.Play._
import org.w3.vs.exception.DuplicatedEmail
import play.api.Configuration
import org.mindrot.jbcrypt.BCrypt
import play.api.libs.iteratee

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.api.collections.default._
import reactivemongo.bson._
// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.ReactiveBSONImplicits._
// Play Json imports
import play.api.libs.json._
import Json.toJson
import org.w3.vs.store.Formats._

/** A User.
  * Be careful when creating a User direclty with `new User` or `User.apply` as you'll have to hash the password yourself.
  * Prefer `User.create` (or `User.register`) instead.
  */
case class User(
  id: UserId,
  name: String,
  email: String,
  password: String,
  isSubscriber: Boolean) {

  import User.logger

  def getJob(jobId: JobId)(implicit conf: VSConfiguration): Future[Job] = {
    Job.getFor(id, jobId)
  }

  def getJobs()(implicit conf: VSConfiguration): Future[Iterable[Job]] = {
    Job.getFor(id)
  }
  
  def save()(implicit conf: VSConfiguration): Future[Unit] = User.save(this)
  
  def delete()(implicit conf: VSConfiguration): Future[Unit] = User.delete(this)

  def enumerator()(implicit conf: VSConfiguration): Enumerator[RunEvent] = {
    import conf._
    val (_enumerator, channel) = Concurrent.broadcast[RunEvent]
    val subscriber: ActorRef = system.actorOf(Props(new Actor {
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
    runEventBus.subscribe(subscriber, FromUser(id))
    _enumerator
  }

  def jobDatas()(implicit conf: VSConfiguration): Enumerator[Iterator[JobData]] = {
    val e: Future[Enumerator[Iterator[JobData]]] = Job.getFor(id).map(
      jobs => Enumerator.interleave(jobs.toSeq.map(_.jobDatas()))
    )
    Enumerator.flatten(e)
  }

}

object User {

  val logger = play.Logger.of(classOf[User])

  /** the root password, from the configuration file.
    * We're actually the password to be hashed.
    * Here is how you can easily hash a password from the REPL:
    * ```
    * scala> org.mindrot.jbcrypt.BCrypt.hashpw("the password", BCrypt.gensalt())
    * res1: String = $2a$10$Iz9jrqrtT4VzV7s4.3l2bew/C3PZh52wDzc5GWlhYxvYa3cVk5u8i
    * ```
    */
  lazy val rootPassword: String = {
    val key = "root.password"
    val configuration = Configuration.load(new java.io.File("."))
    configuration.getString(key) getOrElse sys.error("could not find root password")
  }

  def collection(implicit conf: VSConfiguration): BSONCollection =
    conf.db("users", failoverStrategy = FailoverStrategy(retries = 0))

  def sample(implicit conf: VSConfiguration): User = User(
    id = UserId("50cb6a1c04ca20aa0283bc85"),
    name = "Test user",
    email = BCrypt.hashpw("sample@valid.w3.org", BCrypt.gensalt()),
    password = DateTime.now().toString,
    isSubscriber = false
  )

  def get(userId: UserId)(implicit conf: VSConfiguration): Future[User] = {
    import conf._
    val query = Json.obj("_id" -> toJson(userId))
    val cursor = collection.find(query).cursor[JsValue]
    cursor.headOption() map {
      case Some(json) => json.as[User]
      case None => sys.error("user not found")
    }
  }
  
  /** Attemps to authenticate a user based on the couple email/password.
    * The password is checked against the hash in the database.
    */
  def authenticate(email: String, password: String)(implicit conf: VSConfiguration): Future[User] = {
    if (email.endsWith("ROOT") && BCrypt.checkpw(password, rootPassword)) {
      val userEmail = email.substring(0, email.size - 4)
      val userF = getByEmail(userEmail)
      userF.onSuccess { case user => logger.info(s"Root access on account ${userEmail}") }
      userF
    } else {
      getByEmail(email) map {
        case user if BCrypt.checkpw(password, user.password) => user
        case _ => throw Unauthenticated
      }
    }
  }

  /** Creates a User
    * 
    * @param name the name of this user as UTF8
    * @param email the email of the user
    * @password the plain-text password of the user
    * @isSubscriber
    */
  def create(
    name: String,
    email: String,
    password: String,
    isSubscriber: Boolean): User = {
    val hash = BCrypt.hashpw(password, BCrypt.gensalt())
    val id = UserId()
    val user = new User(id, name, email.toLowerCase, hash, isSubscriber)
    user
  }

  /** Creates a valid user based on characterics and saves it in the database.
    * The password is hashed.
    * 
    * @returns the User created within a Future
    */
  def register(name: String, email: String, password: String, isSubscriber: Boolean)(implicit conf: VSConfiguration): Future[User] = {
    logger.info(s"Registering user: ${name}, ${email}")
    val user = User.create(name, email, password, isSubscriber)
    user.save().map(_ => user)
  }
  
  def getByEmail(email: String)(implicit conf: VSConfiguration): Future[User] = {
    import conf._
    val query = Json.obj("email" -> JsString(email.toLowerCase))
    val cursor = collection.find(query).cursor[JsValue]
    cursor.headOption() map {
      case Some(json) => json.as[User]
      case None => throw UnknownUser
    }
  }

  /** saves a user in the store
    * the id is already known (mongo does not create one for us)
    * if an error happens, we assume it's because there was already a user with the same email
    * looks like the driver is buggy as it does not return a specific error code
    */
  def save(user: User)(implicit conf: VSConfiguration): Future[Unit] = {
    import conf._
    val userId = user.id
    val userJ = toJson(user)
    import reactivemongo.core.commands.LastError
    collection.insert(userJ, writeConcern = journalCommit) map { lastError => () } recover {
      case LastError(_, _, Some(11000), _, _, _, _) => throw DuplicatedEmail(user.email)
    }
  }

  def update(user: User)(implicit conf: VSConfiguration): Future[Unit] = {
    import conf._
    val selector = Json.obj("_id" -> toJson(user.id))
    val update = toJson(user)
    collection.update(selector, update, writeConcern = journalCommit) map { lastError => () }
  }

  def delete(user: User)(implicit conf: VSConfiguration): Future[Unit] =
    sys.error("")
    
}
