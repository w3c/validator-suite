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

  def apply(
      id: OrganizationId = OrganizationId(),
      name: String,
      admin: User): Organization =
    Organization(OrganizationVO(id, name, admin.id))
  
  def get(id: OrganizationId)/*(implicit configuration: VSConfiguration, context: ExecutionContext)*/: FutureVal[Exception, Organization] = {
    //import configuration.store
    //store.getOrganizations(id = id).map(t=>t.headOption)
    //FutureVal.failed(new Exception("not implemented"))
    sys.error("ni")
  }
  def getForAdmin(admin: UserId): FutureVal[Exception, Iterable[Organization]] = sys.error("ni")
  
  def save(organization: Organization): FutureVal[Exception, Organization] = sys.error("")
  
}

case class OrganizationVO(
    id: OrganizationId = OrganizationId(),
    name: String,
    admin: UserId)

case class Organization(valueObject: OrganizationVO) {
  
  val logger = play.Logger.of(classOf[Organization])
  
  def id: OrganizationId = valueObject.id
  def name: String = valueObject.name
  
  def getAdmin: FutureVal[Exception, User] = User.get(valueObject.admin)
  
  def save(): FutureVal[Exception, Organization] = Organization.save(this)
  
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
