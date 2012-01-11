package org.w3.vs.model

/** An Assertion from an [[org.w3.vs.validator.Assertor]]
 * 
 *  It's made of a series of [[org.w3.vs.validator.Event]]s
 */
case class Assertion(events: Seq[Event]) {

  /** having no errors means having no event with severity as 'error' */
  def hasError: Boolean =
    events exists { event => event.severity == "error" }

  def errorsNumber: Int =
    events count { event => event.severity == "error" }
  
  def warningsNumber: Int =
    events count { event => event.severity == "warning" }
}