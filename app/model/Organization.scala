package org.w3.vs.model

import org.w3.vs._
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.dispatch._
import java.nio.channels.ClosedChannelException
import org.w3.util.akkaext.Deafen
import org.w3.util.akkaext.Listen
import org.w3.vs.VSConfiguration
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.PushEnumerator
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
  
  def subscribeToUpdates()(implicit conf: VSConfiguration): Enumerator[RunUpdate] = {
    import conf.system
    val organizationPath = system / "organizations" / id.toString
    val organizationRef = system.actorFor(organizationPath)
    lazy val subscriber: ActorRef = system.actorOf(Props(new Actor {
      def receive = {
        case msg: RunUpdate =>
          try {
            enumerator.push(msg)
          } catch {
            case e: ClosedChannelException => enumerator.close; logger.error("ClosedChannel exception: ", e)
            case e => enumerator.close; logger.error("Enumerator exception: ", e)
          }
        case _ => ()
      }
    }))
    lazy val enumerator: PushEnumerator[RunUpdate] =
      Enumerator.imperative[RunUpdate](
        onComplete = () => {organizationRef ! Deafen(subscriber); logger.info("onComplete")},
        onError = (_,_) => () => {organizationRef ! Deafen(subscriber); logger.info("onError")}
      )
    organizationRef ! Listen(subscriber)
    enumerator &> Enumeratee.onIterateeDone(() => {organizationRef ! Deafen(subscriber); logger.info("onIterateeDone")}) 
  }
  
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
