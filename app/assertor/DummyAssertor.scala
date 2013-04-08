/*package org.w3.vs.assertor

import org.w3.vs.util._
import org.w3.vs.model._
import scalaz._

/**
 * An Assertor that builds dummy assertions
 * For testing only
 */
object DummyAssertor extends FromURLAssertor {

  //val id = AssertorId("DummyAssertor")
  
  // really dumb
  def validatorURLForMachine(url: URL) = url
  
  override def assert(url: URL)(implicit context: ExecutionContext): FutureVal[Throwable, Iterable[Assertion]] = 
    FutureVal.successful{
      val event = Assertion(
        severity = Info,
        assertId = "dummy",
        lang = "en",
        title = "Dummy title",
        contexts = Seq(Context("Dummy context", None, None)),
        description = Some("Dummy message"))
      Seq(event)
  }
  
}
*/