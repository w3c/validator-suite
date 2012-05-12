package org.w3.vs.model

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

object Organization {

//  def apply(organizationId: OrganizationId)(implicit conf: VSConfiguration): Organization =
//    new Organization(organizationId)
  
  def apply(name: String): Organization =
    new Organization(OrganizationId(), name)
  
  def fake() = Organization(OrganizationId(), name = "@@ fake organization @@")

  def get(id: OrganizationId)(implicit configuration: VSConfiguration, context: ExecutionContext): FutureVal[Exception, Organization] = {
    import configuration.store
    //store.getOrganizations(id = id).map(t=>t.headOption)
    FutureVal.failed(new Exception("not implemented"))
  }
  
}

case class Organization(id: OrganizationId, name: String) {

  val logger = play.Logger.of(classOf[Organization])

  def subscribeToUpdates()(implicit conf: VSConfiguration): Enumerator[RunUpdate] = {
    import conf.system
    val organizationPath = system / "organizations" / id.toString
    val organizationRef = system.actorFor(organizationPath)
    lazy val subscriber: ActorRef = system.actorOf(Props(new Actor {
      def receive = {
        case msg: UpdateData =>
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

}
