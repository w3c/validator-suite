package org.w3.vs.model

import org.w3.util.URL
import org.joda.time._
import org.w3.vs.assertor._
import scalaz.Validation

sealed trait AssertorResponse {
  val id: AssertorResponseId
  val jobId: JobId
  val assertorId: AssertorId
  val sourceUrl: URL
  val timestamp: DateTime
}

case class AssertorResult(
    id: AssertorResponseId = AssertorResponseId(),
    jobId: JobId,
    assertorId: AssertorId,
    sourceUrl: URL,
    timestamp: DateTime = DateTime.now,
    assertions: Iterable[Assertion]) extends AssertorResponse {
  
  def isValid = ! hasError
  
  def hasError: Boolean = assertions exists {_.severity == Error}
  def hasWarnings: Boolean = assertions exists {_.severity == Warning}
  
  def numberOfErrors = assertions.view.filter(_.severity == Error).size
  def numberOfWarnings = assertions.view.filter(_.severity == Warning).size
  
}

case class AssertorFailure(
    id: AssertorResponseId = AssertorResponseId(),
    jobId: JobId,
    assertorId: AssertorId,
    sourceUrl: URL,
    timestamp: DateTime = new DateTime,
    why: String) extends AssertorResponse


/** An event coming from an observation
 * 
 *  @param severity a severity (either: error, warning, info)
 *  @param id identifier that defines uniquely this kind of event
 *  @param lang XML lang code
 *  @param contexts a sequence of [[org.w3.vs.validator.Context]]s
 */
case class Assertion(
    id: AssertionId = AssertionId(),
    assertorResponseId: AssertorResponseId, 
    url: URL,
    lang: String,
    title: String,
    severity: AssertionSeverity,
    contexts: Seq[Context],
    description: Option[String])

sealed trait AssertionSeverity
case object Error extends AssertionSeverity
case object Warning extends AssertionSeverity
case object Info extends AssertionSeverity

/** A context for an [[org.w3.vs.validator.Event]]
 *
 *  @param content a code snippet from the source
 *  @param line an optional line in the source
 *  @param column an optional column in the source
 */
case class Context(
    id: ContextId = ContextId(),
    assertionId: AssertionId,
    content: String, 
    line: Option[Int], 
    column: Option[Int]) // TODO remove ref


