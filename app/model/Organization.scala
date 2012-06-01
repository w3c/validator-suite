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

case class Organization(
    id: OrganizationId = OrganizationId(),
    name: String,
    adminId: UserId)(implicit conf: VSConfiguration) {
  
  val logger = play.Logger.of(classOf[Organization])
  
  def getAdmin: FutureVal[Exception, User] = User.get(adminId)
  
  def save(): FutureVal[Exception, Organization] = Organization.save(this)
  
  lazy val (enumerator, channel) = Concurrent.broadcast[RunUpdate]
  
  def toValueObject: OrganizationVO = OrganizationVO(id, name, adminId)

}

object Organization {
  
  def get(id: OrganizationId)(implicit conf: VSConfiguration): FutureVal[Exception, Organization] = {
    implicit def ec = conf.webExecutionContext
    FutureVal.successful(play.api.Global.w3c)
  }
  
  def getForAdmin(admin: UserId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Organization]] = 
    sys.error("ni")
  
  def save(organization: Organization)(implicit conf: VSConfiguration): FutureVal[Exception, Organization] = {
    implicit def ec = conf.webExecutionContext
    FutureVal.successful(organization)
  }
  
}
