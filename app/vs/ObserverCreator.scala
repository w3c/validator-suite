package org.w3.vs

import akka.actor.{ActorSystem, TypedActor, TypedProps}
import org.w3.vs.http.{Http, HttpImpl}
import org.w3.vs.observer._
import org.w3.vs.model._
import org.w3.vs.assertor._
import akka.util.duration._
import akka.util.Duration

trait ObserverCreator {
  def byObserverId(observerId: ObserverId): Option[Observer]
  def observerOf(
    observerId: ObserverId,
    strategy: Strategy): Observer
}
    
class ObserverCreatorImpl()(implicit val configuration: ValidatorSuiteConf) extends ObserverCreator {
  
  var registry = Map[ObserverId, Observer]()
  
  def byObserverId(observerId: ObserverId): Option[Observer] =
    registry.get(observerId)
    
  def observerOf(
      observerId: ObserverId,
      strategy: Strategy): Observer = {
    val obs = TypedActor(TypedActor.context).typedActorOf(
      TypedProps(
        classOf[Observer],
        new ObserverImpl(observerId, strategy)),
      observerId.toString())
    registry += (observerId -> obs)
    obs
  }
}

