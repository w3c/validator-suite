package org.w3.vs

import akka.actor.ActorSystem
import akka.actor.TypedActor
import org.w3.vs.http.{Http, HttpImpl}
import akka.actor.Props
import org.w3.vs.observer._
import org.w3.vs.model._
import org.w3.vs.assertor._
import akka.util.duration._
import akka.util.Duration

trait Production extends ValidatorSuiteConf {
  
  val system: ActorSystem = ActorSystem("vs")
  
  lazy val http: Http =
    TypedActor(system).typedActorOf(
      classOf[Http],
      new HttpImpl(),
      Props(),
      "http")
  
  // ouch :-)
  http.authorityManagerFor("w3.org").sleepTime = 0
  
  lazy val observerCreator: ObserverCreator =
    TypedActor(system).typedActorOf(
      classOf[ObserverCreator],
      new ObserverCreatorImpl()(this),
      Props(),
      "observer")
      
  val assertorPicker: AssertorPicker = SimpleAssertorPicker
  
}
