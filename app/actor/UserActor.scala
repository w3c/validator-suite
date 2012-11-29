package org.w3.vs.actor

import akka.actor._
import org.w3.util.akkaext._
import org.w3.vs._
import org.w3.vs.actor.message._
import org.w3.vs.model._

class UserActor(user: User)(implicit val configuration: VSConfiguration)
extends Actor with PathAwareActor with Listeners {

  val logger = play.Logger.of(classOf[UserActor])

  val jobsRef: ActorRef = context.actorOf(Props(new JobsActor()).withDispatcher("user-dispatcher"), name = "jobs")
  
  def receive: Actor.Receive = listenerHandler orElse {
    case Tell(path, msg) if path == selfPath => self ! msg
    case m: Tell => jobsRef.forward(m)
    case m: RunUpdate => tellListeners(m)
  }

}
