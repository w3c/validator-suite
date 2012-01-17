package org.w3.vs

import akka.actor.ActorSystem
import akka.actor.TypedActor
import http.Http
import http.HttpImpl
import akka.actor.Props
import org.w3.vs.observer._
import org.w3.vs.model._
import org.w3.vs.assertor._
import akka.util.duration._
import akka.util.Duration

trait ObserverCreator {
  def byObserverId(observerId: ObserverId): Option[Observer]
  def observerOf(observerId: ObserverId,
    strategy: Strategy,
    assertorPicker: AssertorPicker = SimpleAssertorPicker,
    timeout: Duration = 10.second): Observer
}
    
class ObserverCreatorImpl extends ObserverCreator {
  
  var registry = Map[ObserverId, Observer]()
  
  def byObserverId(observerId: ObserverId): Option[Observer] =
    registry.get(observerId)
    
  def observerOf(
      observerId: ObserverId,
      strategy: Strategy,
      assertorPicker: AssertorPicker = SimpleAssertorPicker,
      timeout: Duration = 10.second): Observer = {
    val obs = TypedActor(TypedActor.context).typedActorOf(
      classOf[Observer],
      new ObserverImpl(assertorPicker, observerId, strategy) with ObserverSubscribers,
      Props(),
      observerId.toString())
    registry += (observerId -> obs)
    obs
  }
}

object GlobalSystem {
  
  def init(): Unit = {
    
    if (system != null) {
      TypedActor(system).stop(http)
      TypedActor(system).stop(observerCreator)
      system.shutdown()
      system = null
    }
    
    system = ActorSystem("VSSystem")
  
    http =
      TypedActor(system).typedActorOf(
        classOf[Http],
        new HttpImpl(),
        Props(),
        "http")

    observerCreator = 
      TypedActor(GlobalSystem.system).typedActorOf(
      classOf[ObserverCreator],
      new ObserverCreatorImpl(),
      Props(),
      "observer")

  }
  
  var system: ActorSystem = null
  
  var http: Http = null

  var observerCreator: ObserverCreator = null
  
}