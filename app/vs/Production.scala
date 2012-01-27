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
import akka.dispatch.ExecutionContext

trait Production extends ValidatorSuiteConf {
  
  val MAX_URL_TO_FETCH = 10
  
  val validatorDispatcher: ExecutionContext = {
    import java.util.concurrent.{ExecutorService, Executors}
    val executor: ExecutorService = Executors.newFixedThreadPool(10)
    ExecutionContext.fromExecutorService(executor)
  }
  
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
