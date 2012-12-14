package org.w3.vs.model

import org.w3.util._
import scalaz.std.string._
import scalaz.Scalaz.ToEqualOps
import org.w3.vs.exception._
import org.w3.vs._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee.{Concurrent, Enumerator}
import org.w3.vs.actor.message.RunUpdate
import akka.actor.{Actor, Props, ActorRef}
import java.nio.channels.ClosedChannelException
import org.w3.util.akkaext.{Deafen, Listen, PathAware}
import org.joda.time.DateTime

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

case class User(id: UserId, vo: UserVO)(implicit conf: VSConfiguration) {
  
  import conf.system

  val logger = play.Logger.of(classOf[User])

  def isSubscriber = vo.isSubscriber

  // getJob with id only if owned by user. should probably be a db request directly.
  def getJob(jobId: JobId): Future[Job] = {
    Job.getFor(id) map {
      jobs => jobs.filter(_.id === jobId).headOption.getOrElse { throw UnknownJob(jobId) }
    }
  }

  def getJobs(): Future[Iterable[Job]] = {
    Job.getFor(id)
  }
  
  def save(): Future[Unit] = User.save(this)
  
  def delete(): Future[Unit] = User.delete(this)

  lazy val enumerator: Enumerator[RunUpdate] = {
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
    listen(subscriber)
    _enumerator
  }

  def listen(implicit listener: ActorRef): Unit =
    PathAware(usersRef, path).tell(Listen(listener), listener)

  def deafen(implicit listener: ActorRef): Unit =
    PathAware(usersRef, path).tell(Deafen(listener), listener)

  val usersRef = system.actorFor(system / "users")

  private val path = system / "users" / id.toString
  
}

object User {

  def collection(implicit conf: VSConfiguration): DefaultCollection =
    conf.db("users")

  val logger = play.Logger.of(classOf[User])

  def sample(implicit conf: VSConfiguration) = User.apply(
    userId = UserId("50cb6a1c04ca20aa0283bc85"),
    name = "Test user",
    email = "sample@valid.w3.org",
    password = DateTime.now().toString,
    isSubscriber = false
  )

  def apply(
    userId: UserId,
    name: String,
    email: String,
    password: String,
    isSubscriber: Boolean)(
    implicit conf: VSConfiguration): User =
      User(userId, UserVO(name, email, password, isSubscriber))

  def get(userId: UserId)(implicit conf: VSConfiguration): Future[User] = {
    import conf._
    val query = Json.obj("_id" -> toJson(userId))
    val cursor = collection.find[JsValue, JsValue](query)
    cursor.toList map { _.headOption match {
      case Some(json) => {
        val userVo = json.as[UserVO]
        User(userId, userVo)
      }
      case None => sys.error("user not found")
    }}
  }
  
  def authenticate(email: String, password: String)(implicit conf: VSConfiguration): Future[User] = {
    getByEmail(email) map { 
      case user if (user.vo.password /== password) => throw Unauthenticated
      case user => user
    }
  }

  def register(email: String, name: String, password: String, isSubscriber: Boolean)(implicit conf: VSConfiguration): Future[User] = {
    val user = User(UserId(), email, name, password, isSubscriber)
    user.save().map(_ => user)
  }
  
  def getByEmail(email: String)(implicit conf: VSConfiguration): Future[User] = {
    import conf._
    val query = Json.obj("email" -> JsString(email))
    val cursor: FlattenedCursor[JsValue] = collection.find[JsValue, JsValue](query)
    cursor.toList map { _.headOption match {
      case Some(json) => {
        val id = (json \ "_id").as[UserId]
        val userVo = json.as[UserVO]
        User(id, userVo)
      }
      case None => throw UnknownUser
    }}
  }

  /** saves a user in the store
    * the id is already known (mongo does not create one for us)
    * if an error happens, we assume it's because there was already a user with the same email
    * looks like the driver is buggy as it does not return a specific error code
    */
  def save(user: User)(implicit conf: VSConfiguration): Future[Unit] = {
    import conf._
    val userId = user.id
    val userJ = toJson(user.vo).asInstanceOf[JsObject] + ("_id" -> toJson(userId))
    import reactivemongo.core.commands.LastError
    collection.insert(userJ) map { lastError => () } recover {
      case LastError(_, _, Some(11000), _, _) => throw DuplicatedEmail(user.vo.email)
    }
  }

  def delete(user: User)(implicit conf: VSConfiguration): Future[Unit] =
    sys.error("")
    
}
