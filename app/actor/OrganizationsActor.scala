package org.w3.vs.actor

import akka.actor._
import akka.dispatch._
import akka.pattern.ask
import org.w3.vs.http._
import org.w3.vs.model._
import org.w3.vs.assertor._
import org.w3.vs.VSConfiguration
import scalaz._
import Scalaz._
import akka.util.Timeout
import akka.util.duration._
import org.w3.vs.exception.Unknown
import message._
import org.w3.util.akkaext._

case class CreateOrganizationAndForward(organizationData: OrganizationData, tell: Tell)

class OrganizationsActor()(implicit configuration: VSConfiguration) extends Actor with PathAwareActor {

  val logger = play.Logger.of(classOf[OrganizationsActor])

  def getOrganizationRefOrCreate(organizationData: OrganizationData): ActorRef = {
    val id = organizationData.id
    val name = id.toString
    try {
      context.actorOf(Props(new OrganizationActor(organizationData)), name = name)
    } catch {
      case iane: InvalidActorNameException => context.actorFor(self.path / name)
    }
  }

  def receive = {

    case tell @ Tell(Child(name), msg) => {
      val from = sender
      val to = self
      context.children.find(_.path.name === name) match {
        case Some(organizationRef) => organizationRef forward tell
        case None => {
          val uri = configuration.binders.OrganizationUri(name)
          val orga = {
            import org.w3.vs.store.Store.fromTryCatch
            fromTryCatch(configuration.stores.OrganizationStore.get(uri))(configuration.storeExecutionContext)
          }

          orga.asFuture onSuccess {
            case Success(organizationData) => to.tell(CreateOrganizationAndForward(organizationData, tell), from)
            case Failure(storeException) => logger.error(storeException.toString)
          }
        }
      }
    }

    case CreateOrganizationAndForward(organizationData, tell) => {
      val organizationRef = getOrganizationRefOrCreate(organizationData)
      organizationRef forward tell
    }

  }

}

