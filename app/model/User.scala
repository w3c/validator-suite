package org.w3.vs.model

import org.w3.util._
import scalaz.std.string._
import scalaz.Scalaz.ToEqualOps
import org.w3.vs.exception._
import org.w3.banana._
import org.w3.banana.LinkedDataStore._
import org.w3.vs._
import org.w3.vs.store.Binders._
import org.w3.vs.sparql._
import org.w3.vs.diesel._
import org.w3.vs.diesel.ops._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee.{Concurrent, Enumerator}
import org.w3.vs.actor.message.RunUpdate
import akka.actor.{Actor, Props, ActorRef}
import java.nio.channels.ClosedChannelException
import org.w3.util.akkaext.{Deafen, Listen, PathAware}

case class User(id: UserId, vo: UserVO)(implicit conf: VSConfiguration) {
  
  import conf._

  val userUri = id.toUri

  val ldr: LinkedDataResource[Rdf] = LinkedDataResource[Rdf](userUri, vo.toPG)

  val orgUriOpt = vo.organization map (_.toUri)

  def getOrganization(): Future[Option[Organization]] = {
    orgUriOpt match {
      case Some(orgUri) => Organization.bananaGet(orgUri) map (Some(_))
      case None => (None: Option[Organization]).asFuture
    }
  }

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
  
  val logger = play.Logger.of(classOf[User])

  def apply(
    userId: UserId,
    name: String,
    email: String,
    password: String,
    organization: Option[OrganizationId])(
    implicit conf: VSConfiguration): User =
      User(userId, UserVO(name, email, password, organization))

  def bananaGet(userUri: Rdf#URI)(implicit conf: VSConfiguration): Future[User] = {
    import conf._
    for {
      userId <- userUri.as[UserId].asFuture
      userLDR <- store.asLDStore.GET(userUri)
      userVO <- userLDR.resource.as[UserVO].asFuture
    } yield new User(userId, userVO) { override val ldr = userLDR }
  }

  def get(userUri: Rdf#URI)(implicit conf: VSConfiguration): Future[User] = {
    import conf._
    bananaGet(userUri)
  }
  
  def get(id: UserId)(implicit conf: VSConfiguration): Future[User] =
    get(UserUri(id))

  def authenticate(email: String, password: String)(implicit conf: VSConfiguration): Future[User] = {
    getByEmail(email) map { 
      case user if (user.vo.password /== password) => throw Unauthenticated
      case user => user
    }
  }

  def register(email: String, name: String, password: String)(implicit conf: VSConfiguration): Future[User] = {
    val user = User(userId = UserId(), organization = Some(OrganizationId()), email = email, name = name, password = password)
    user.save().map(_ => user)
  }
  
  def getByEmail(email: String)(implicit conf: VSConfiguration): Future[User] = {
    import conf._
    val query = """
CONSTRUCT {
  <local:user> <local:hasUri> ?user .
  ?s ?p ?o
} WHERE {
  graph ?userG {
    ?user ont:email ?email .
    ?s ?p ?o
  }
}
"""
    val construct = ConstructQuery(query, ont)
    val r = for {
      graph <- store.executeConstruct(construct, Map("email" -> email.toNode))
      as <- (PointedGraph[Rdf](URI("local:user"), graph) / URI("local:hasUri")).as2[UserId, UserVO].asFuture
    } yield User(as._1, as._2)
    r recover { case _: Exception => throw UnknownUser }
  }

  def save(vo: UserVO)(implicit conf: VSConfiguration): Future[Rdf#URI] = {
    import conf._
    store.asLDStore.POSTToCollection(userContainer, vo.toPG)
  }
  
  def save(user: User)(implicit conf: VSConfiguration): Future[Unit] = {
    import conf._
    store.asLDStore.PUT(user.ldr)
  }

  def delete(user: User)(implicit conf: VSConfiguration): Future[Unit] =
    sys.error("")
    
}
