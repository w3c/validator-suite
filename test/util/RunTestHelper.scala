package org.w3.vs.util

import org.w3.vs.model._
import akka.testkit.{ TestKit, ImplicitSender }
import org.w3.vs.VSConfiguration
import org.scalatest._
import org.scalatest.matchers.MustMatchers
import org.w3.util.website._
import org.w3.vs.store.MongoStore
import org.w3.util.Util._

/**
 * helper trait that can be used to test Runs
 * Be careful: all TypedActors are stopped after each test
 */
abstract class RunTestHelper(configuration: VSConfiguration = new DefaultTestConfiguration { })
extends TestKit(configuration.system) with ImplicitSender
with WordSpec with MustMatchers with BeforeAndAfterAll {
  
  def servers: Seq[Webserver]
  
  implicit val implicitConfiguration: VSConfiguration = configuration

  val http = configuration.httpActorRef

  implicit val timeout = configuration.timeout

  val runEventBus = configuration.runEventBus

  val userTest = User.create(email = "", name = "", password = "", isSubscriber = true)
  
  val logger = play.Logger.of(classOf[RunTestHelper])
  
  val configurationBeforeTest = org.w3.vs.Prod.configuration
  
  override def beforeAll: Unit = {
    org.w3.vs.Prod.configuration = configuration
    MongoStore.reInitializeDb().getOrFail()
    servers foreach { _.start() }
  }
  
  override def afterAll: Unit = {
//    reactivemongo.api.MongoConnection.system.shutdown()
//    reactivemongo.api.MongoConnection.system.awaitTermination()
    configuration.httpClient.close()
    configuration.connection.close()
    configuration.system.shutdown()
    configuration.system.awaitTermination()
    servers foreach { _.stop() }
    org.w3.vs.Prod.configuration = configurationBeforeTest
  }
  
}

