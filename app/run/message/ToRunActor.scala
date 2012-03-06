package org.w3.vs.run.message

import akka.actor.ActorRef

case object Start
case object Stop
case object GetStatus
case object GetJobData
case class Subscribe(subscriber: ActorRef)
case class Unsubscribe(subscriber: ActorRef)
