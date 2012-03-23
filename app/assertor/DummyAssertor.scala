package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import scalaz.Success

/**
 * An Assertor that builds dummy assertions
 * For testing only
 */
object DummyAssertor extends FromURLAssertor {

  val id = AssertorId("DummyAssertor")
  
  // really dumb
  def validatorURLForMachine(url: URL) = url
  
  override def assert(url: URL) = {
    val event = RawAssertion(
        severity="info",
        assertId="dummy",
        lang="en",
        title="Dummy title",
        contexts=Seq(Context("Dummy context", url.toString, None, None)),
        description = Some("Dummy message"))
    Success(Seq(event))
  }
  
}
