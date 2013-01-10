package org.w3.vs.actor

import akka.actor._
import org.w3.util.akkaext._
import org.w3.vs._
import org.w3.vs.actor.message._
import org.w3.vs.model._

object UserActor {

  val logger = play.Logger.of(classOf[UserActor])

}

class UserActor(userId: UserId)(implicit val configuration: VSConfiguration)
extends Actor with Listeners {

  import UserActor.logger

  def receive: Actor.Receive = listenerHandler orElse {
    case m: RunUpdate => tellListeners(m)
    case a => logger.error(s"UserActor(%{userId}): unexpected %{a}")
  }

}
