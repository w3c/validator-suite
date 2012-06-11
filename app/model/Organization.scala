package org.w3.vs.model

import org.w3.vs._
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.dispatch._
import java.nio.channels.ClosedChannelException
import org.w3.vs.VSConfiguration
import play.api.libs.iteratee._
import org.w3.vs.actor.message._
import org.w3.vs.model._
import org.w3.util._
import org.w3.util.akkaext._
import org.w3.banana._

case class Organization(
    id: OrganizationId = OrganizationId(),
    name: String,
    adminId: UserId)(implicit conf: VSConfiguration) {
  
  import conf.system
  implicit def timeout = conf.timeout
  private val organizationsRef = system.actorFor(system / "organizations")
  private val path = system / "organizations" / id.toString
  
  val logger = play.Logger.of(classOf[Organization])
  
  def getAdmin: FutureVal[Exception, User] = User.get(adminId)
  
  def save(): FutureVal[Exception, Unit] = Organization.save(this)
  
  import akka.pattern.ask
  
  def enumerator: Enumerator[RunUpdate] = {
    implicit def ec = conf.webExecutionContext
    val enum = (PathAware(organizationsRef, path) ? GetOrgEnumerator).mapTo[Enumerator[RunUpdate]]
    Enumerator.flatten(enum failMap (_ => Enumerator.eof[RunUpdate]) toPromise) // TODO log error
  }
  
  def toValueObject: OrganizationVO = OrganizationVO(id, name, adminId)

}

object Organization {
  
  def apply(vo: OrganizationVO)(implicit conf: VSConfiguration): Organization =
    Organization(vo.id, vo.name, vo.admin)

  def getOrganizationVO(id: OrganizationId)(implicit conf: VSConfiguration): FutureVal[Exception, OrganizationVO] = {
    import conf.binders._
    implicit val context = conf.webExecutionContext
    val uri = OrganizationUri(id)
    FutureVal(conf.store.getNamedGraph(uri)) flatMap { graph => 
      FutureVal.pureVal[Throwable, OrganizationVO]{
        val pointed = PointedGraph(uri, graph)
        OrganizationVOBinder.fromPointedGraph(pointed)
      }(t => t)
    }
  }

  def get(id: OrganizationId)(implicit conf: VSConfiguration): FutureVal[Exception, Organization] =
    getOrganizationVO(id) map { Organization(_) }
  
//  def getForAdmin(admin: UserId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Organization]] = 
//    sys.error("ni")

  def save(organization: Organization)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] =
    saveOrganizationVO(organization.toValueObject)
  
  def saveOrganizationVO(vo: OrganizationVO)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] = {
    import conf.binders._
    implicit val context = conf.webExecutionContext
    val graph = OrganizationVOBinder.toPointedGraph(vo).graph
    val result = conf.store.addNamedGraph(OrganizationUri(vo.id), graph)
    FutureVal(result)
  }

}
