package org.w3.vs.model

import org.w3.util.URL
import org.joda.time._
import java.util.UUID
import org.w3.vs.assertor._

/** An Assertion from an [[org.w3.vs.validator.Assertor]]
 * 
 *  It's made of a series of [[org.w3.vs.validator.Event]]s
 */
case class Assertion(
    id: Assertion#Id = UUID.randomUUID(),
    // the urls that were needed to generate this assertion
    url: URL,
    assertorId: AssertorId,
    // an assertion was produced in the context of a run
    jobId: Job#Id,
    // when this assertion was produced
    timestamp: DateTime = new DateTime,
    // the useful stuff
    result: AssertionResult) {
  type Id = UUID
}

sealed trait AssertionResult

// when producing an assertion ended with an error
case class AssertionError(why: String) extends AssertionResult

// a successful assertion made of events (ala Unicorn)
case class Events(events: Seq[Event]) extends AssertionResult {
  
  def toIterable: Iterable[Event] = events
  
  /** having no errors means having no event with severity as 'error' */
  def hasError: Boolean =
    events exists { event => event.severity == "error" }

  def errorsNumber: Int =
    events count { event => event.severity == "error" }
  
  def warningsNumber: Int =
    events count { event => event.severity == "warning" }
  
}