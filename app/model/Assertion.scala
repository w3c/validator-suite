package org.w3.vs.model

import org.w3.util.URL
import org.w3.vs.assertor.AssertorId

/** An Assertion from an [[org.w3.vs.validator.Assertor]]
 * 
 *  It's made of a series of [[org.w3.vs.validator.Event]]s
 */
case class Assertion(url: URL, assertorId: AssertorId, result: AssertionResult)

sealed trait AssertionResult

case class AssertionError(why: Throwable) extends AssertionResult

case class Events(events: Seq[Event]) extends AssertionResult with Iterable[Event] {
  
  def iterator: Iterator[Event] = events.iterator
  
  /** having no errors means having no event with severity as 'error' */
  def hasError: Boolean =
    events exists { event => event.severity == "error" }

  def errorsNumber: Int =
    events count { event => event.severity == "error" }
  
  def warningsNumber: Int =
    events count { event => event.severity == "warning" }
  
}