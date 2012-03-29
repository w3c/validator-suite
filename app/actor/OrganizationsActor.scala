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

class OrganizationsActor()(implicit configuration: VSConfiguration) extends Actor {

  val logger = play.Logger.of(classOf[OrganizationsActor])

  logger.debug("started")

  def getOrCreateOrganization(id: OrganizationId): ActorRef = {
    val name = id.toString
    context.children.find(_.path.name === name) getOrElse {
      configuration.store.getOrganizationDataById(id) match {
        case Failure(t) => throw t
        case Success(None) => sys.error("couldn't find the data in store for " + name)
        case Success(Some(organizationData)) =>
          context.actorOf(Props(new OrganizationActor(organizationData)), name = name)
      }
    }
  }

  def receive = {
    case m: Message => {
      val organizationRef = getOrCreateOrganization(m.organizationId)
      organizationRef.forward(m)
    }
  }

}

