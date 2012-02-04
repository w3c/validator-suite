package org.w3.vs.observer

import org.w3.vs.model.{Strategy, ObserverId}
import org.w3.vs.http.Http
import akka.actor.{Actor, TypedActor}
import akka.testkit.TestKit
import akka.util.Duration
import java.util.concurrent.TimeUnit._
import org.specs2.mutable._
import org.specs2.specification.Example
import org.specs2.specification.Scope
import akka.actor.ActorSystem
import org.w3.vs.{ValidatorSuiteConf, Production}

/**
 * helper trait that can be used to test Observers
 * Be careful: all TypedActors are stopped after each test
 */
class ObserverScope(servers: Seq[unfiltered.util.RunnableServer])(implicit val configuration: ValidatorSuiteConf) extends TestKit(configuration.system) with Scope with BeforeAfter {
  
  val observerCreator = configuration.observerCreator
  val http = configuration.http
  
  val logger = play.Logger.of(classOf[ObserverScope])
  
  override def before: Any = {
    servers foreach { _.start() }
  }
  
  override def after: Any = {
    servers foreach { _.stop() }
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

