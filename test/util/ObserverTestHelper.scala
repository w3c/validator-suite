package org.w3.vs.observer

import org.w3.vs.model.{Strategy, ObserverId}
import org.w3.vs.http.Http
import akka.actor.{Actor, TypedActor}
import akka.testkit.{TestKit, ImplicitSender}
import akka.util.Duration
import java.util.concurrent.TimeUnit._
import akka.actor.ActorSystem
import org.w3.vs.{ValidatorSuiteConf, Production}
import org.scalatest._
import org.scalatest.matchers.MustMatchers

/**
 * helper trait that can be used to test Observers
 * Be careful: all TypedActors are stopped after each test
 */
abstract class ObserverTestHelper(configuration: ValidatorSuiteConf)
extends TestKit(configuration.system) with ImplicitSender
with WordSpec with MustMatchers with BeforeAndAfterAll {
  
  def servers: Seq[unfiltered.util.RunnableServer]
  
  val observerCreator = configuration.observerCreator
  val http = configuration.http
  
  val logger = play.Logger.of(classOf[ObserverTestHelper])
  
  override def beforeAll: Unit = {
    servers foreach { _.start() }
  }
  
  override def afterAll: Unit = {
    configuration.system.shutdown()
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

