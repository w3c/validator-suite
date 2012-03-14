package org.w3.vs.run.message

import akka.actor.ActorRef

case object Run
case object Stop
case object RunNow

case object GetStatus
case object GetJobData
case object TellTheWorldYouAreAlive
case class Subscribe(subscriber: ActorRef)
case class Unsubscribe(subscriber: ActorRef)
