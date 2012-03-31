package org.w3.vs.actor

import akka.actor._
import akka.dispatch._
import akka.pattern.ask
import org.w3.vs.http.{ Http, HttpImpl }
import org.w3.vs.model._
import org.w3.vs.assertor._
import org.w3.vs.VSConfiguration
import scalaz._
import Scalaz._
import akka.util.Timeout
import akka.util.duration._
import org.w3.vs.exception.Unknown
import message._

case class CreateOrganizationAndForward(organizationData: OrganizationData, msg: Message, sender: ActorRef)

class OrganizationsActor()(implicit configuration: VSConfiguration) extends Actor {

  val logger = play.Logger.of(classOf[OrganizationsActor])

  def getOrganizationRefOrCreate(organizationData: OrganizationData): ActorRef = {
    val id = organizationData.id
    val name = id.toString
    try {
      context.actorOf(Props(new OrganizationActor(organizationData)), name = name)
    } catch {
      case iane: InvalidActorNameException => context.children.find(_.path.name === name).get //context.actorFor(self.path / name)
    }
  }

  def thesender = context.sender

  def receive = {

    case msg: Message => {
      val id = msg.organizationId
      val name = id.toString
      println("%%%"+sender.path)
      println("%%%"+context.children.map(_.path.name).mkString(":::"))
      context.children.find(_.path.name === name) match {
        case Some(organizationRef) => organizationRef forward msg
        case None => {
          configuration.store.getOrganizationDataById(id).asFuture onSuccess {
            case Success(organizationData) => {
              println("{{{ "+thesender)
              self forward CreateOrganizationAndForward(organizationData, msg, thesender)
            }
            case Failure(storeException) => sys.error(storeException.toString)
          }
        }
      }
    }

    case CreateOrganizationAndForward(organizationData, msg, s) => {
      println("%%%$$ "+s.path)
      val organizationRef = getOrganizationRefOrCreate(organizationData)
      organizationRef.tell(msg, s)
    }

  }

}

