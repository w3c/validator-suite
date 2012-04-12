package org.w3.vs.actor

import java.util.UUID
import org.w3.vs.model._
import org.w3.vs._
import akka.dispatch._
import akka.actor._
import akka.util.Timeout
import akka.util.duration._
import akka.pattern.ask
import play.api.libs.iteratee.{Enumerator, PushEnumerator}
import java.nio.channels.ClosedChannelException
import org.w3.util.akkaext._
import play.api.libs.iteratee.Enumeratee
import message.UpdateData

object Organization {

  def apply(organizationId: OrganizationId)(implicit conf: VSConfiguration): Organization =
    new Organization(organizationId)

  def apply(organizationData: OrganizationData)(implicit conf: VSConfiguration): Organization =
    new Organization(organizationData.id)

}

class Organization(organizationId: OrganizationId)(implicit conf: VSConfiguration) {

  import conf.system

  val logger = play.Logger.of(classOf[Organization])

  val organizationPath = system / "organizations" / organizationId.toString
  val organizationRef = system.actorFor(organizationPath)

  def subscribeToUpdates(): Enumerator[message.RunUpdate] = {
    lazy val subscriber: ActorRef = system.actorOf(Props(new Actor {
      def receive = {
        case msg: message.UpdateData =>
          try {
            enumerator.push(msg)
          } catch {
            case e: ClosedChannelException => enumerator.close; logger.error("ClosedChannel exception: ", e)
            case e => enumerator.close; logger.error("Enumerator exception: ", e)
          }
        case _ => ()
      }
    }))
    lazy val enumerator: PushEnumerator[message.RunUpdate] =
      Enumerator.imperative[message.RunUpdate](
        onComplete = () => {organizationRef ! Deafen(subscriber); logger.info("onComplete")},
        onError = (_,_) => () => {organizationRef ! Deafen(subscriber); logger.info("onError")}
      )
    organizationRef ! Listen(subscriber)
    enumerator &> Enumeratee.onIterateeDone(() => {organizationRef ! Deafen(subscriber); logger.info("onIterateeDone")}) 
  }



}
