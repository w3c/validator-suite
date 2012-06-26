package org.w3.vs.actor

import org.w3.vs._
import org.w3.vs.model._
import akka.actor._
import System.{ currentTimeMillis => now }
import org.w3.vs.actor.message._
import org.w3.util.akkaext._

class OrganizationActor(organization: Organization)(implicit val configuration: VSConfiguration)
extends Actor with PathAwareActor with Listeners {

  val logger = play.Logger.of(classOf[OrganizationActor])

  val jobsRef: ActorRef = context.actorOf(Props(new JobsActor()), name = "jobs")
  
  def receive: Actor.Receive = listenerHandler orElse {
    case Tell(path, msg) if path == selfPath => self ! msg
    case m: Tell => jobsRef.forward(m)
    case m: RunUpdate => tellListeners(m)
  }

}
