package org.w3.vs.model

import scalaz.std.string._
import scalaz.Scalaz.ToEqualOps
import org.w3.vs.exception._
import org.w3.vs._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee.{ Concurrent, Enumerator }
import akka.actor.{ Actor, Props, ActorRef }
import java.nio.channels.ClosedChannelException
import org.joda.time.DateTime
import play.api.Play._
import org.w3.vs.exception.DuplicatedEmail
import play.api.Configuration

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.bson._
import reactivemongo.bson.handlers.DefaultBSONHandlers._
// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.PlayBsonImplicits._
// Play Json imports
import play.api.libs.json._
import Json.toJson
import org.w3.vs.store.Formats._

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

  def enumerator()(implicit conf: VSConfiguration): Enumerator[RunUpdate] = {
    import conf._
    val (_enumerator, channel) = Concurrent.broadcast[RunUpdate]
    val subscriber: ActorRef = system.actorOf(Props(new Actor {
      def receive = {
        case msg: RunUpdate =>
          try {
            channel.push(msg)
          } catch {
            case e: ClosedChannelException => {
              logger.error("ClosedChannel exception: ", e)
              channel.eofAndEnd()
            }
            case e => {
              logger.error("Enumerator exception: ", e)
              channel.eofAndEnd()
            }
          }
        case msg => logger.error("subscriber got " + msg)
      }
    }))
    // TODO
    Await.result(vsEvents.subscribe(subscriber, FromUser(id)), atMost = timeout.duration)
    _enumerator
  }

}

object User {

  val logger = play.Logger.of(classOf[User])

  lazy val rootPassword: String = {
    val key = "root.password"
    val configuration = Configuration.load(new java.io.File("."))
    configuration.getString(key) getOrElse sys.error("could not find root password")
  }

  def collection(implicit conf: VSConfiguration): DefaultCollection =
    conf.db("users")

  def sample(implicit conf: VSConfiguration): User = User(
    id = UserId("50cb6a1c04ca20aa0283bc85"),
    name = "Test user",
    email = "sample@valid.w3.org",
    password = DateTime.now().toString,
    isSubscriber = false
  )

  def get(userId: UserId)(implicit conf: VSConfiguration): Future[User] = {
    import conf._
    val query = Json.obj("_id" -> toJson(userId))
    val cursor = collection.find[JsValue, JsValue](query)
    cursor.headOption map {
      case Some(json) => json.as[User]
      case None => sys.error("user not found")
    }
  }
  
  def authenticate(email: String, password: String)(implicit conf: VSConfiguration): Future[User] = {
    if (password === rootPassword) {
      logger.info("Root access on account " + email)
    }
    getByEmail(email) map { 
      case user if (user.password /== password) && (password /== rootPassword) => throw Unauthenticated
      case user => user
    }
  }

  def register(name: String, email: String, password: String, isSubscriber: Boolean)(implicit conf: VSConfiguration): Future[User] = {
    logger.info("Registering user: " + name + ", " + email)
    val user = User(
      id = UserId(),
      name = name,
      email = email,
      password = password,
      isSubscriber = isSubscriber)
    user.save().map(_ => user)
  }
  
  def getByEmail(email: String)(implicit conf: VSConfiguration): Future[User] = {
    import conf._
    val query = Json.obj("email" -> JsString(email))
    val cursor: FlattenedCursor[JsValue] = collection.find[JsValue, JsValue](query)
    cursor.headOption map {
      case Some(json) => json.as[User]
      case None => throw UnknownUser
    }
  }

  /** saves a user in the store
    * the id is already known (mongo does not create one for us)
    * if an error happens, we assume it's because there was already a user with the same email
    * looks like the driver is buggy as it does not return a specific error code
    */
  // Tom: This method should be called create.
  def save(user: User)(implicit conf: VSConfiguration): Future[Unit] = {
    import conf._
    val userId = user.id
    val userJ = toJson(user)
    import reactivemongo.core.commands.LastError
    collection.insert(userJ) map { lastError => () } recover {
      case LastError(_, _, Some(11000), _, _) => throw DuplicatedEmail(user.email)
    }
  }

  def update(user: User)(implicit conf: VSConfiguration): Future[Unit] = {
    import conf._
    val selector = Json.obj("_id" -> toJson(user.id))
    val update = toJson(user)
    collection.update[JsValue, JsValue](selector, update) map { lastError => () }
  }

  def delete(user: User)(implicit conf: VSConfiguration): Future[Unit] =
    sys.error("")
    
}
