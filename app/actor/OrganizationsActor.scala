package org.w3.vs.actor

import akka.actor._
import akka.dispatch._
import akka.pattern.ask
import org.w3.vs.http._
import org.w3.vs.model._
import org.w3.util._
import org.w3.vs.assertor._
import org.w3.vs.VSConfiguration
import scalaz._
import Scalaz._
import akka.util.Timeout
import akka.util.duration._
import message._
import org.w3.util.akkaext._

case class CreateOrganizationAndForward(organization: Organization, tell: Tell)

class OrganizationsActor()(implicit configuration: VSConfiguration) extends Actor with PathAwareActor {

  val logger = play.Logger.of(classOf[OrganizationsActor])

  def getOrganizationRefOrCreate(organization: Organization): ActorRef = {
    val id = organization.id.toString
    try {
      context.actorOf(Props(new OrganizationActor(organization)), name = id)
    } catch {
      case iane: InvalidActorNameException => context.actorFor(self.path / id)
    }
  }

  def receive = {

    case tell @ Tell(Child(id), msg) => {
      val from = sender
      val to = self
      context.children.find(_.path.name === id) match {
        case Some(organizationRef) => organizationRef forward tell
        case None => {
          logger.info("Creating OrganizationActor: " + id)
          Organization.get(OrganizationId(id)) onComplete {
            case Success(organization) => to.tell(CreateOrganizationAndForward(organization, tell), from)
            case Failure(exception) => logger.error("Couldn't find organization with id: " + id, exception)
          }
        }
      }
    }

    case CreateOrganizationAndForward(organization, tell) => {
      val organizationRef = getOrganizationRefOrCreate(organization)
      organizationRef forward tell
    }
    
    case a => {logger.error("unexpected message: " + a.toString)}

  }

}
