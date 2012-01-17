package org.w3.vs

import akka.actor.ActorSystem
import akka.actor.TypedActor
import http.Http
import http.HttpImpl
import akka.actor.Props

object GlobalSystem {
  
  var system = ActorSystem("VSSystem")
  
  val http = 
    TypedActor(system).typedActorOf(
      classOf[Http],
      new HttpImpl(),
      Props(),
      "http")

  
}