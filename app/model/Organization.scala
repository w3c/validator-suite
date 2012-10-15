package org.w3.vs.model

import akka.actor._
import java.nio.channels.ClosedChannelException
import org.w3.vs.VSConfiguration
import play.api.libs.iteratee._
import org.w3.vs.actor.message._
import org.w3.util._
import org.w3.util.akkaext._
import org.w3.banana._
import org.w3.banana.LinkedDataStore._
import org.w3.vs._
import diesel._
import org.w3.vs.store.Binders._
import scala.concurrent._

case class Organization(id: OrganizationId, vo: OrganizationVO)(implicit conf: VSConfiguration) {
  
  import conf._

  val orgUri = id.toUri
  val adminUri = vo.admin.toUri

  val name = vo.name

  val ldr: LinkedDataResource[Rdf] = LinkedDataResource(orgUri, vo.toPG)

  val organizationsRef = system.actorFor(system / "organizations")

  private val path = system / "organizations" / id.toString

  val logger = play.Logger.of(classOf[Organization])
  
  def getAdmin(): Future[User] =
    User.bananaGet(adminUri).toFutureVal
  
  def save(): Future[Unit] = Organization.save(this)

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
    PathAware(organizationsRef, path).tell(Listen(listener), listener)
  
  def deafen(implicit listener: ActorRef): Unit =
    PathAware(organizationsRef, path).tell(Deafen(listener), listener)
  
}

object Organization {

  def apply(
    orgId: OrganizationId,
    name: String,
    admin: UserId)(
    implicit conf: VSConfiguration): Organization =
      Organization(orgId, OrganizationVO(name, admin))

  def extractId(uri: Rdf#URI): String = uri.getString substring organizationContainer.getString.length
  
  def bananaGet(orgUri: Rdf#URI)(implicit conf: VSConfiguration): Future[Organization] = {
    import conf._
    for {
      orgId <- OrganizationUri.fromUri(orgUri).asFuture
      orgLDR <- store.GET(orgUri)
      orgVO <- orgLDR.resource.as[OrganizationVO]
    } yield new Organization(orgId, orgVO) { override val ldr = orgLDR }
  }

  def get(orgUri: Rdf#URI)(implicit conf: VSConfiguration): Future[Organization] = {
    import conf._
    bananaGet(orgUri).toFutureVal
  }
  
  def get(id: OrganizationId)(implicit conf: VSConfiguration): Future[Organization] = {
    import conf._
    get(OrganizationUri(id))
  }
  
//  def getForAdmin(admin: UserId)(implicit conf: VSConfiguration): Future[Iterable[Organization]] = 
//    sys.error("ni")

  def save(organization: Organization)(implicit conf: VSConfiguration): Future[Unit] = {
    import conf._
    store.PUT(organization.ldr).toFutureVal
  }

  def setAdmin(orgUri: Rdf#URI, adminUri: Rdf#URI)(implicit conf: VSConfiguration): Future[Unit] = {
    import conf._
    store.POST(orgUri, orgUri -- ont.admin ->- adminUri).map{ _ => () }.toFutureVal
  }
  
  def save(vo: OrganizationVO)(implicit conf: VSConfiguration): Future[Rdf#URI] = {
    import conf._
    store.POSTToCollection(organizationContainer, OrganizationVOBinder.toPointedGraph(vo)).toFutureVal
  }

  def addUser(orgUri: Rdf#URI, userUri: Rdf#URI)(implicit conf: VSConfiguration): Future[Unit] = {
    import conf._
    val script = for {
      _ <- Command.POST[Rdf](orgUri, orgUri -- ont.user ->- userUri)
      _ <- Command.POST[Rdf](userUri, userUri -- ont.organization ->- orgUri)
    } yield ()
    store.execute(script)
  }

}
