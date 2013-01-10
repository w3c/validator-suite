package org.w3.vs.actor

import akka.actor._
import org.w3.util.akkaext._
import org.w3.vs.VSConfiguration
import org.w3.vs.model._
import scala.util._
import scalaz.Scalaz._

object UsersActor {

  val logger = play.Logger.of(classOf[UsersActor])

  case class Forward(msg: Any, to: UserId)

}

class UsersActor()(implicit conf: VSConfiguration) extends Actor {

  import UsersActor.{ logger, Forward }

  var map: Map[UserId, ActorRef] = Map.empty

  def getUserRefOrCreate(userId: UserId): ActorRef = {
    map.get(userId) getOrElse {
      context.actorOf(Props(new UserActor(userId)))
    }
  }

  def receive = {

    case Forward(msg, userId) => {
      // println(s"passing message to child ${msg}: ${id}")
      val userActorRef = getUserRefOrCreate(userId)
      userActorRef.forward(msg)
    }

    case a => {
      logger.error(s"users-actor: unexpected ${a}")
    }

  }

}
