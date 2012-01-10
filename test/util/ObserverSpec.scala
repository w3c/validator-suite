package org.w3.vs.util

import org.w3.vs.model.{Strategy, ObserverId}
import org.w3.vs.assertor.{AssertorPicker, DoNothingAssertorPicker}
import org.w3.vs.http.Http
import org.w3.vs.observer.{Observer, ObserverImpl, DoNothingBroadcaster}
import akka.actor.{Actor, TypedActor}
import akka.testkit.TestKit
import akka.util.Duration
import java.util.concurrent.TimeUnit._
import org.specs2.mutable._
import org.specs2.specification.Example

/**
 * helper trait that can be used to test Observers
 * Be careful: all TypedActors are stopped after each test
 */
trait ObserverSpec extends Specification with BeforeAfter /*with TestKit*/ {
  
  val logger = play.Logger.of(classOf[ObserverSpec])
  
  val http = Http.getInstance()
  
  var observers = Seq[Observer]()

  override def before: Any = {
    observers = Seq[Observer]()
  }
  
  override def after: Any = {
    Actor.registry foreachTypedActor { TypedActor stop _ }
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
  
  /**
   * provides a context to body
   * The servers are started before the execution of body, then stopped
   * It's obviously not thread-safe and tests need to be run sequentially
   */
  def observe(servers: Seq[unfiltered.util.RunnableServer])(body: => Example): Example =
    try {
      servers foreach { _.start() }
      val e = body
      observers foreach testInvariants
      e
    } finally {
      servers foreach { _.stop() }
    }
  
  /**
   * test the invariant that must be true for all observations
   */
  def testInvariants(observer: Observer): Example = {
    logger.debug("TODO: for every authority, delay was respected")
    logger.debug("TODO: for every authority, no fetch overlap")
    1 must beEqualTo(1)
  }
  
}

