package org.w3.vs.observer

import org.w3.vs.model.{Strategy, ObserverId}
import org.w3.vs.assertor.{AssertorPicker, DoNothingAssertorPicker}
import org.w3.vs.http.Http
import akka.actor.{Actor, TypedActor}
import akka.testkit.TestKit
import akka.util.Duration
import java.util.concurrent.TimeUnit._
import org.specs2.mutable._
import org.specs2.specification.Example
import org.specs2.specification.Scope
import org.w3.vs.Global
import akka.actor.ActorSystem

/**
 * helper trait that can be used to test Observers
 * Be careful: all TypedActors are stopped after each test
 */
class ObserverScope(servers: Seq[unfiltered.util.RunnableServer])(system: ActorSystem) extends TestKit(system) with Scope with BeforeAfter {
  
  val logger = play.Logger.of(classOf[ObserverScope])
  
  var observers = Seq[Observer]()
  
  override def before: Any = {
    observers = Seq[Observer]()
    servers foreach { _.start() }
  }
  
  override def after: Any = {
    servers foreach { _.stop() }
    system.shutdown()
    Global.system = null
  }
  
  /**
   * helper method to create new observers
   * the created observers don't react on broadcast messages
   */
  def newObserver(
      strategy: Strategy,
      assertorPicker: AssertorPicker = DoNothingAssertorPicker,
      timeout: Duration = Duration(5, SECONDS)) = {
    val observerId = ObserverId()
    val observer =
      TypedActor.newInstance(
        classOf[Observer],
        new ObserverImpl(http, assertorPicker, observerId, strategy) with DoNothingBroadcaster,
        timeout.toMillis)
     observers :+= observer
     observer
  }
  
//  /**
//   * test the invariant that must be true for all observations
//   */
//  def testInvariants(observer: Observer): Example = {
//    logger.debug("TODO: for every authority, delay was respected")
//    logger.debug("TODO: for every authority, no fetch overlap")
//    1 must beEqualTo(1)
//  }
  
}

