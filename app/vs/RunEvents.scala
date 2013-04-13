package org.w3.vs

import org.w3.vs.actor.{RunsActor, RunEventBusActor, RunEventBus}
import akka.actor.{ActorRef, Props}

trait RunEvents {
  this: ValidatorSuite
    with ActorSystem
    with Database
    with HttpClient =>

  def runEventBus: RunEventBus

  def runsActorRef: akka.actor.ActorRef

}

trait DefaultRunEvents extends RunEvents {
  this: ValidatorSuite
    with ActorSystem
    with Database
    with HttpClient =>

  val runEventBus: RunEventBus = {
    val actorRef = system.actorOf(Props(new RunEventBusActor()(this)), "runevent-bus")
    RunEventBus(actorRef)
  }

  val runsActorRef: ActorRef =
    system.actorOf(Props(new RunsActor()(this)), "runs")

}