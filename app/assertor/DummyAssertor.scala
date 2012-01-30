package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._

/**
 * An Assertor that builds dummy assertions
 * For testing only
 */
object DummyAssertor extends FromURLAssertor {

  val id = AssertorId("DummyAssertor")
  
  // really dumb
  def validatorURLForMachine(url: URL) = url
  
  override def assert(url: URL): Asserted = {
    val event = Event(
        severity="info",
        id="dummy",
        lang="en",
        contexts=Seq(Context("", url.toString, None, None)))
    Asserted(Seq(event))
  }
  
}