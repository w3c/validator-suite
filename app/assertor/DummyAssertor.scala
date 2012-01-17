package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import akka.dispatch._
import akka.actor.TypedActor

/**
 * An Assertor that builds dummy assertions
 * For testing only
 */
object DummyAssertor extends FromURLAssertor {

  import TypedActor.dispatcher
  
  val id = AssertorId("DummyAssertor")
  
  // really dumb
  def validatorURLForMachine(url: URL) = url
  
  override def assert(url: URL): Future[Assertion] = Future {
    val event = Event(
        severity="info",
        id="dummy",
        lang="en",
        contexts=Seq(Context("", url.toString, None, None)))
    Assertion(Seq(event))
  }
  
}