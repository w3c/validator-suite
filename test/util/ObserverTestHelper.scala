package org.w3.vs.run

import org.w3.vs.model._
import org.w3.vs.http.Http
import akka.actor.{Actor, TypedActor}
import akka.testkit.{TestKit, ImplicitSender}
import akka.util.Duration
import java.util.concurrent.TimeUnit._
import akka.actor.ActorSystem
import org.w3.vs.VSConfiguration
import org.scalatest._
import org.scalatest.matchers.MustMatchers

/**
 * helper trait that can be used to test Runs
 * Be careful: all TypedActors are stopped after each test
 */
abstract class RunTestHelper(configuration: VSConfiguration)
extends TestKit(configuration.system) with ImplicitSender
with WordSpec with MustMatchers with BeforeAndAfterAll {
  
  def servers: Seq[unfiltered.util.RunnableServer]
  
  val runCreator = configuration.runCreator
  val http = configuration.http
  val store = configuration.store
  
  val logger = play.Logger.of(classOf[RunTestHelper])
  
  val configurationBeforeTest = org.w3.vs.Prod.configuration
  
  override def beforeAll: Unit = {
    org.w3.vs.Prod.configuration = configuration
    servers foreach { _.start() }
  }
  
  override def afterAll: Unit = {
    configuration.system.shutdown()
    servers foreach { _.stop() }
    org.w3.vs.Prod.configuration = configurationBeforeTest
  }
  
//  /**
//   * test the invariant that must be true for all observations
//   */
//  def testInvariants(run: Run): Example = {
//    logger.debug("TODO: for every authority, delay was respected")
//    logger.debug("TODO: for every authority, no fetch overlap")
//    1 must beEqualTo(1)
//  }
  
}

