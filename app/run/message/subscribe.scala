package org.w3.vs.run.message

import akka.actor.ActorRef

sealed trait SpeakToRun

case object Start extends SpeakToRun
case object Stop extends SpeakToRun
case object GetStatus extends SpeakToRun
case object GetJobData extends SpeakToRun
case class Subscribe(subscriber: ActorRef) extends SpeakToRun
case class Unsubscribe(subscriber: ActorRef) extends SpeakToRun
